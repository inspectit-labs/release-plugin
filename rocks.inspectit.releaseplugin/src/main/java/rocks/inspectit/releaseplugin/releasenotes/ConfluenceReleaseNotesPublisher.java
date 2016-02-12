package rocks.inspectit.releaseplugin.releasenotes;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.apache.commons.lang.text.StrSubstitutor;
import org.kohsuke.stapler.DataBoundConstructor;

import com.atlassian.jira.rest.client.api.domain.Issue;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import rocks.inspectit.releaseplugin.AbstractJIRAConfluenceAction;
import rocks.inspectit.releaseplugin.ConfluenceAccessTool;
import rocks.inspectit.releaseplugin.JIRAAccessTool;
import rocks.inspectit.releaseplugin.credentials.ConfluenceCredentials;
import rocks.inspectit.releaseplugin.credentials.JIRAProjectCredentials;

/**
 * 
 * Tool for publishing a list of tickets on a confluence page.
 * 
 * @author Jonas Kunz
 *
 */
public class ConfluenceReleaseNotesPublisher extends AbstractJIRAConfluenceAction {
	
	/**
	 * The space under which the page shal lbe published.
	 */
	private String spaceKey;
	/**
	 * The JQL filter of the tickets to list.
	 */
	private String jqlFilter;
	/**
	 * Title of the page to publish.
	 */
	private String pageTitle;
	/**
	 * Title of the parent page.
	 */
	private String parentPageTitle;
	
	


	/**
	 * 
	 * Databound constructor, called by Jenkins.
	 * 
	 * @param jiraCredentialsID the jira credentials
	 * @param confluenceCredentialsID the confluence credentials
	 * @param spaceKey the space key
	 * @param jqlFilter the jql filter
	 * @param pageTitle the title of the new page
	 * @param parentPageTitle the title of the parent page
	 */
	@DataBoundConstructor
	public ConfluenceReleaseNotesPublisher(String jiraCredentialsID,
			String confluenceCredentialsID, String spaceKey, String jqlFilter,
			String pageTitle, String parentPageTitle) {
		super(jiraCredentialsID, confluenceCredentialsID);
		this.spaceKey = spaceKey;
		this.jqlFilter = jqlFilter;
		this.pageTitle = pageTitle;
		this.parentPageTitle = parentPageTitle;
	}

	public String getSpaceKey() {
		return spaceKey;
	}

	public String getJqlFilter() {
		return jqlFilter;
	}
	
	public String getPageTitle() {
		return pageTitle;
	}
	
	public String getParentPageTitle() {
		return parentPageTitle;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

		
		StrSubstitutor varReplacer = getVariablesSubstitutor(build, listener);
		PrintStream logger = listener.getLogger();

		JIRAProjectCredentials jiraCred = getJiraCredentials();
		ConfluenceCredentials confCred = getConfluenceCredentials();
		
		JIRAAccessTool jira = new JIRAAccessTool(jiraCred.getUrl(), jiraCred.getUrlUsername(), jiraCred.getUrlPassword(), jiraCred.getProjectKey(), getJiraCredentialsID());
		ConfluenceAccessTool confluence = new ConfluenceAccessTool(confCred.getUrl(), confCred.getUrlUsername(), confCred.getUrlPassword());

		String jqlFilter = varReplacer.replace(this.jqlFilter);
		String spaceKey = varReplacer.replace(this.spaceKey);
		String pageTitle = varReplacer.replace(this.pageTitle);
		String parentPageTitle = varReplacer.replace(this.parentPageTitle);
		
		List<Issue> tickets = jira.getTicketsByJQL(jqlFilter);
		
		logger.println("Publishing " + tickets.size() + " tickets on page '" + pageTitle + "' in space '" + spaceKey + "' on confluence.");
		
		String pageHTML = jira.buildReleaseNotesHTML(tickets);
		
		Long parentPageID = null;
		if (!parentPageTitle.isEmpty()) {
			List<Long> results = confluence.getPageIDByTitle(parentPageTitle, spaceKey);		
			if (results.size() == 0) {				
				throw new RuntimeException("No page with title '" + parentPageTitle + "' found!");
			}
			if (results.size() > 1) {				
				throw new RuntimeException("Multiple pages with title '" + parentPageTitle + "' found!");
			}
			parentPageID = results.get(0);
		}
		
		
		confluence.createPage(pageTitle, pageHTML, spaceKey, parentPageID);
			

		confluence.destroy();
		jira.destroy();

		return true;
	}





	/**
	 * Descriptor class.
	 * @author JKU
	 *
	 */
	@Extension
	public static class DescriptorImpl extends AbstractJIRAConfluenceAction.DescriptorImpl {

		/**
		 * Constructor.
		 */
		public DescriptorImpl() {
			super();
			load();
		}

		@Override
		public String getDisplayName() {
			return "Publish Release Notes on Confluence";
		}

	}

}
