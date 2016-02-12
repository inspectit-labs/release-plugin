package rocks.inspectit.releaseplugin.ticketing;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.text.StrSubstitutor;
import org.kohsuke.stapler.DataBoundConstructor;

import rocks.inspectit.releaseplugin.AbstractJIRAAction;
import rocks.inspectit.releaseplugin.JIRAAccessTool;
import rocks.inspectit.releaseplugin.credentials.JIRAProjectCredentials;


/**
 * Plugin allowing to add and modify an arbitrary number of Tickets.
 * 
 * @author Jonas Kunz
 *
 */
public class JIRATicketEditor extends AbstractJIRAAction {

	/**
	 * List of templates for ticket creations.
	 */
	List<AddTicketTemplate> newTicketsTemplates;
	
	/**
	 * List of templates for ticket modifications.
	 */
	List<ModifyTicketsTemplate> modifyTicketsTemplates;
	

	/**
	 * Databound constructor, called by Jenkins.
	 * @param jiraCredentialsID the jria credentials
	 * @param newTicketsTemplates the templates for new tickets
	 * @param modifyTicketsTemplates the templates for modifications.
	 */
	@DataBoundConstructor
	public JIRATicketEditor(String jiraCredentialsID,
			List<AddTicketTemplate> newTicketsTemplates, List<ModifyTicketsTemplate> modifyTicketsTemplates) {
		super(jiraCredentialsID);

		this.newTicketsTemplates = newTicketsTemplates == null ? new ArrayList<AddTicketTemplate>() : newTicketsTemplates;
		this.modifyTicketsTemplates = modifyTicketsTemplates == null ? new ArrayList<ModifyTicketsTemplate>() : modifyTicketsTemplates;
	}

	public List<AddTicketTemplate> getNewTicketsTemplates() {
		return newTicketsTemplates;
	}
	
	public List<ModifyTicketsTemplate> getModifyTicketsTemplates() {
		return modifyTicketsTemplates;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

		StrSubstitutor varReplacer = getVariablesSubstitutor(build, listener);
		PrintStream logger = listener.getLogger();

		JIRAProjectCredentials cred = getJiraCredentials();
		
		
		JIRAAccessTool jira = new JIRAAccessTool(cred.getUrl(), cred.getUrlUsername(), cred.getUrlPassword(), cred.getProjectKey(), getJiraCredentialsID());

		
		for (ModifyTicketsTemplate temp : modifyTicketsTemplates) {
			temp.applyModifications(jira, varReplacer, logger, build);
		}
		
		for (AddTicketTemplate temp : newTicketsTemplates) {
			temp.publishTicket(jira, varReplacer, logger);
		}
		
	
		
		jira.destroy();

		return true;
	}
	
	
	
	/**
	 * The descriptor class.
	 * @author JKU
	 *
	 */
	@Extension
	public static class DescriptorImpl extends
			AbstractJIRAAction.DescriptorImpl {

		/**
		 * Constructor.
		 */
		public DescriptorImpl() {
			super();
			load();
		}

		@Override
		public String getDisplayName() {
			return "JIRA Ticket Editor";
		}

	}
}
