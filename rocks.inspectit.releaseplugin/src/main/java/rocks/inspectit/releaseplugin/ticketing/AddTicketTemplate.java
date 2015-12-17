package rocks.inspectit.releaseplugin.ticketing;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.ComboBoxModel;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.text.StrSubstitutor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import rocks.inspectit.releaseplugin.JIRAAccessTool;
import rocks.inspectit.releaseplugin.JIRAMetadataCache;
import rocks.inspectit.releaseplugin.JIRAAccessTool.BuildingLambda;

import com.atlassian.jira.rest.client.api.domain.BasicIssueType;
import com.atlassian.jira.rest.client.api.domain.BasicPriority;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.Version;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;

/**
 * 
 * Form for entering the data of a new Ticket to create.
 * 
 * @author Jonas Kunz
 *
 */
public class AddTicketTemplate extends AbstractDescribableImpl<AddTicketTemplate> {
	
	/**
	 * if true, tickets will only be published if they don't already exist.
	 */
	private boolean performDuplicateCheck;
	
	/**
	 * JQL selecting the parent ticket in case of the newly created ticket is a subticket.
	 */
	private String parentJQL;
	
	/**
	 * the title of the new ticket.
	 */
	private String title;
	
	/**
	 * The name of the type of the new ticket.
	 */
	private String type;
	/**
	 * The affected version of this ticket, can be empty.
	 */
	private String affectedVersion;
	/**
	 * The priority of this new ticket.
	 */
	private String priority;
	/**
	 * The long description text of the ticket.
	 */
	private String description;	

	
	

	/**
	 * Databound constructor, called by jenkins.
	 * @param performDuplicateCheck value of performDuplicateCheck
	 * @param parentJQL the jql fitler selecting the parent
	 * @param title the title
	 * @param type the type
	 * @param affectedVersion the affected version
	 * @param priority the priority
	 * @param description the description
	 */
	@DataBoundConstructor
	public AddTicketTemplate(boolean performDuplicateCheck, String parentJQL, String title, String type,
			String affectedVersion, String priority, String description) {
		super();
		this.performDuplicateCheck = performDuplicateCheck;
		this.parentJQL = parentJQL;
		this.title = title;
		this.type = type;
		this.affectedVersion = affectedVersion;
		this.priority = priority;
		this.description = description;
	}

	public String getTitle() {
		return title;
	}
	
	public String getType() {
		return type;
	}
	
	public String getPriority() {
		return priority;
	}

	public String getDescription() {
		return description;
	}
	

	public boolean getPerformDuplicateCheck() {
		return performDuplicateCheck;
	}

	public String getAffectedVersion() {
		return affectedVersion;
	}

	public String getParentJQL() {
		return parentJQL;
	}
	/**
	 * Publishes this TIcket on JIRA, if it does not yet exist.
	 * A ticket is considered to be already existing, when a ticket with matching version and title already exists.
	 *  
	 * @param jira
	 * 		the tool to access jira
	 * @param varReplacer
	 * 		the variables to be replaced
	 * @param logger
	 * 		log printstream
	 */
	public void publishTicket(JIRAAccessTool jira, StrSubstitutor varReplacer, PrintStream logger) {
		final String title = varReplacer.replace(this.title);
		String type = varReplacer.replace(this.type);
		String affectedVersion = varReplacer.replace(this.affectedVersion);
		String priority = varReplacer.replace(this.priority);
		final String description = varReplacer.replace(this.description);
		String parentJQL = varReplacer.replace(this.parentJQL);
	
		if (performDuplicateCheck) {
			String jql = "summary ~ \"" + title + "\"";
			if (!affectedVersion.isEmpty()) {
				jql += " AND affectedVersion = \"" + affectedVersion + "\" ";
			}
			
			/*
			boolean alreadyPresent = 
			jira.getTicketsByJQL(jql)
			.stream().filter(i -> i.getSummary().equalsIgnoreCase(title))
			.count() > 0;
			*/
			boolean alreadyPresent = false;
			for (Issue is : jira.getTicketsByJQL(jql)) {
				if (is.getSummary().equalsIgnoreCase(title)) {
					alreadyPresent = true;
					break;
				}
			}
			
			if (alreadyPresent) {
				logger.println("Skipping Ticket \"" + title + "\", as it is already present.");
				return;
			}
		}
		
		BasicIssueType issueType = jira.getIssueTypeByName(type);
		final BasicPriority issuePriority = jira.getIssuePriorityByName(priority);
		
		final Version version = jira.getVersionByName(affectedVersion);
		

		logger.println("Creating Ticket \"" + title + "\".");
		String parentKey = null;
		
		if (issueType.isSubtask()) {
			List<Issue> result = jira.getTicketsByJQL(parentJQL);
			if (result.size() != 1) {
				throw new RuntimeException("Invalid number of tickets (" + result.size()
						+ ") matching parent JQL '" + parentJQL + "'");
			}
			parentKey = result.get(0).getKey();
		}
		
		final String finalParentKey = parentKey;
		
		jira.addTicket(new BuildingLambda<IssueInputBuilder>() {
			
			@Override
			public void build(IssueInputBuilder b) {
				if (version != null) {
					b.setAffectedVersions(Arrays.asList(version));				
				}
				b.setSummary(title);
				b.setDescription(description);
				if (issuePriority != null) {
					b.setPriority(issuePriority);
				}
				if (finalParentKey != null) {
					b.setFieldValue("parent", ComplexIssueInputFieldValue.with("key", finalParentKey));
					
				}
			}
		}, issueType);
		
		
		
	}


	/**
	 * Descriptor class.
	 * @author JKU
	 *
	 */
	@Extension public static class DescriptorImpl extends Descriptor<AddTicketTemplate> {
        @Override public String getDisplayName() {
            return "New JIRA Ticket Template";
        }
        
        /**
         * Combobox populating method.
         * @param jiraCredentialsID the credentials used for access.
         * @return a ComboBoxModel containing the JIRA values as suggestions
         */
        public ComboBoxModel doFillTypeItems(@RelativePath("..") @QueryParameter String jiraCredentialsID) { 	
        	ComboBoxModel result = new ComboBoxModel();
        	result.addAll(JIRAMetadataCache.getSingleton().getAvailableIssueTypes(jiraCredentialsID));
    		return result;
        }

        /**
         * Combobox populating method.
         * @param jiraCredentialsID the credentials used for access.
         * @return a ComboBoxModel containing the JIRA values as suggestions
         */
        public ComboBoxModel doFillPriorityItems(@RelativePath("..") @QueryParameter String jiraCredentialsID) { 	
        	ComboBoxModel result = new ComboBoxModel();
        	result.addAll(JIRAMetadataCache.getSingleton().getAvailableIssuePriorities(jiraCredentialsID));
    		return result;
        }

        /**
         * Combobox populating method.
         * @param jiraCredentialsID the credentials used for access.
         * @return a ComboBoxModel containing the JIRA values as suggestions
         */
        public ComboBoxModel doFillAffectedVersionItems(@RelativePath("..") @QueryParameter String jiraCredentialsID) { 	
        	ComboBoxModel result = new ComboBoxModel();
        	result.addAll(JIRAMetadataCache.getSingleton().getAvailableVersions(jiraCredentialsID));
    		
    		return result;
        }
        
    }
	
	
}
