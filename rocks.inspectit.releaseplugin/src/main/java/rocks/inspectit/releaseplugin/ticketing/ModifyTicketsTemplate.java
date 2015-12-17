package rocks.inspectit.releaseplugin.ticketing;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.model.Job;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.text.StrSubstitutor;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestCommitDetail;
import org.kohsuke.github.GHRepository;
import org.kohsuke.stapler.DataBoundConstructor;

import rocks.inspectit.releaseplugin.JIRAAccessTool;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.cloudbees.jenkins.GitHubRepositoryName;
import com.cloudbees.jenkins.GitHubRepositoryNameContributor;

/**
 * Form template allowing modifications on Tickets using JQL filters. 
 * 
 * @author Jonas Kunz
 *
 */
public class ModifyTicketsTemplate extends AbstractDescribableImpl<ModifyTicketsTemplate> {

	/**
	 * The name of the regex-group which represents the ticket key.
	 */
	private static final String REGEX_KEY_GROUP_NAME = "key";
	
	/**
	 * the jql fitler used for selecting tickets.
	 */
	private String jqlFilter;
	/**
	 * The regex used to extract Ticket-Keys from git commit messages.
	 */
	private String commitRegEx;
	
	
	/**
	 * The selected source for the tickets. Possible values are:
	 * 	a) JQL - use the given JQL query to select the tickets
	 *  b) GHPullRequest - use the regex to extract the keys fro mthe git commits of the pull request being currently build
	 */
	private String ticketSource;
	
	/**
	 * The modifications to apply.
	 */
	private List<TicketModification> modifications;
	
	

	/**
	 * Databound constructor, called by Jenkins.
	 * @param jqlFilter the jql filter
	 * @param commitRegEx the commit regular expression matcher
	 * @param ticketSource ticket source
	 * @param modifications the modifications
	 */
	@DataBoundConstructor
	public ModifyTicketsTemplate(String jqlFilter, String commitRegEx,
			String ticketSource, List<TicketModification> modifications) {
		super();
		this.jqlFilter = jqlFilter;
		this.commitRegEx = commitRegEx;
		this.ticketSource = ticketSource;
		this.modifications = modifications == null ? new ArrayList<TicketModification>() : modifications;
	}


	public String getJqlFilter() {
		return jqlFilter;
	}

	public List<TicketModification> getModifications() {
		return modifications;
	}
	
	public String getCommitRegEx() {
		return commitRegEx;
	}


	public String getTicketSource() {
		return ticketSource;
	}


	/**
	 * Selects the tickets to modify based on the settings of this template and then applies all modifications on each one.
	 * 
	 * @param jira
	 * 		the jira used for all operations
	 * @param varReplacer
	 * 		the variable replacer. Must especially contain ghprbGhRepository and ghprbPullId in case of pull-request based ticket selection.
	 * @param logger
	 * 		the logger used to print information.
	 * @param build
	 * 		the current build
	 */
	public void applyModifications(JIRAAccessTool jira, StrSubstitutor varReplacer, PrintStream logger, AbstractBuild<?, ?> build) {
		
		
		Set<Issue> issuedToUpdate = new HashSet<>();
		
		
		if ("JQL".equalsIgnoreCase(ticketSource)) {

			String jql = varReplacer.replace(jqlFilter);
			
			issuedToUpdate.addAll(jira.getTicketsByJQL(jql));
			logger.println("Updating " + issuedToUpdate.size() + " Tickets matching filter \"" + jql + "\"");
			
		} else if ("GHPullRequest".equalsIgnoreCase(ticketSource)) {

			
			
			String repoID = varReplacer.getVariableResolver().lookup("ghprbGhRepository");
			String pullReqIDStr = varReplacer.getVariableResolver().lookup("ghprbPullId");
			
			if (repoID == null || pullReqIDStr == null) {
				logger.println("${ghprbGhRepository} or ${ghprbPullId} has not been set, maybe this build wasn't triggered by the pull request builder plugin? Skipping ticket modifications...");
			} else {
				List<String> commitMsgs = extractCommitMessagesFromPullRequest(build, repoID, pullReqIDStr);

				Set<String> ticketKeys = extractTicketKeysFromCommitMessages(varReplacer, commitMsgs);
				
				String jql = buildJQLByTicketKeys(ticketKeys);
				
				issuedToUpdate.addAll(jira.getTicketsByJQL(jql));
			}
			
		}
		
		for (Issue ticket : issuedToUpdate) {
			for (TicketModification modification : modifications) {
				modification.apply(ticket.getKey(), jira, varReplacer, logger);
			}
				
		}
		
		
	}


	/**
	 * Builds a JQL query fetching the tickets with exactly the given keys.
	 * @param ticketKeys
	 * 		the keys of the tickets to fetch.
	 * @return
	 * 		the JQL query.
	 */
	private String buildJQLByTicketKeys(Set<String> ticketKeys) {
		String jql = null;
		for (String key : ticketKeys) {
			if (jql == null) {
				jql = "";
			} else {
				jql += " OR ";						
			}
			jql += "issueKey = \"" + key + "\"";
		}
		return jql;
	}


	/**
	 * Extracts the ticket keys from the given commit messages using the regular expression.
	 * @param varReplacer
	 * 		the varReplacer, which will be applied on the regex in advance
	 * @param commitMsgs the commit messages.
	 * @return a set of all found keys.
	 */
	private Set<String> extractTicketKeysFromCommitMessages(
			StrSubstitutor varReplacer, List<String> commitMsgs) {
		String regex = varReplacer.replace(commitRegEx);
		Pattern regexPattern = Pattern.compile(regex);
		Set<String> ticketKeys = new HashSet<String>();
		for (String msg : commitMsgs) {
			Matcher mat = regexPattern.matcher(msg);
			while (mat.find()) {
				String key = mat.group(REGEX_KEY_GROUP_NAME);
				if (key != null) {
					ticketKeys.add(key.toUpperCase());							
				}
			}
		}
		return ticketKeys;
	}


	/**
	 * Extracts all commit messages of a pull request.
	 * @param build
	 * 		the current jenkins build.
	 * @param repoID
	 * 		the id of the repository e.g. user/repoName
	 * @param pullReqIDStr
	 * 		the id number of the pull request
	 * @return a list of all commit messages
	 */
	private List<String> extractCommitMessagesFromPullRequest(AbstractBuild<?, ?> build,  String repoID, String pullReqIDStr) {	

		List<String> commitMsgs = new ArrayList<String>();
		
		int pullReqID = Integer.parseInt(pullReqIDStr);
		
		GHPullRequest pr = null;
		
		for (GitHubRepositoryName name : GitHubRepositoryNameContributor.parseAssociatedNames((Job<?, ?>) build.getProject())) {
		    for (GHRepository repository : name.resolve()) {
		    	if (repoID.equalsIgnoreCase(repository.getFullName())) {
		    		try {
						pr = repository.getPullRequest(pullReqID);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
		    	}
		    }
		}
		if (pr == null) {
			throw new RuntimeException("Couldn't find pull request #" + pullReqID + " in repo " + repoID + ", maybe it isn't accessible for the Jenkins user?");
		} else {
			
			for (GHPullRequestCommitDetail detail : pr.listCommits()) {
				GHPullRequestCommitDetail.Commit com = detail.getCommit();
				if (com.getMessage() != null) {
					commitMsgs.add(com.getMessage());
				}
			}
		}
		return commitMsgs;
	}
	
	/**
	 * The descriptor class.
	 * @author JKU
	 *
	 */
	@Extension 
	public static class DescriptorImpl extends Descriptor<ModifyTicketsTemplate> {

		@Override
		public String getDisplayName() {
			return "Ticket Modification Filter";
		}
		
	}
	
	
}
