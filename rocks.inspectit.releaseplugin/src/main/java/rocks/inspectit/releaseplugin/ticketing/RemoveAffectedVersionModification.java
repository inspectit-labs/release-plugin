package rocks.inspectit.releaseplugin.ticketing;

import hudson.Extension;
import hudson.model.Descriptor;

import java.io.PrintStream;

import org.apache.commons.lang.text.StrSubstitutor;
import org.kohsuke.stapler.DataBoundConstructor;

import rocks.inspectit.releaseplugin.IssueUpdateBuilder;
import rocks.inspectit.releaseplugin.JIRAAccessTool;
import rocks.inspectit.releaseplugin.JIRAAccessTool.BuildingLambda;

/**
 * 
 * Modification removing a version form the list of affected versions from a ticket.
 * 
 * @author Jonas Kunz
 *
 */
public class RemoveAffectedVersionModification extends TicketModification {

	/**
	 * The Name of the version to remove.
	 */
	private String versionToRemove;
	
	
	/**
	 * Databound Constructor, called by Jenkins.
	 * @param versionToRemove the name of the version to remove
	 */
	@DataBoundConstructor
	public RemoveAffectedVersionModification(String versionToRemove) {
		super();
		this.versionToRemove = versionToRemove;
	}

	public String getVersionToRemove() {
		return versionToRemove;
	}


	/**
	 * Descriptor class.
	 * @author JKU
	 */
	@Extension public static class DescribtorImpl extends Descriptor<TicketModification> {

		@Override
		public String getDisplayName() {
			return "Remove Affected Version";
		}
		
	}


	@Override
	public void apply(String ticketKey, JIRAAccessTool jira, final StrSubstitutor varReplacer, PrintStream logger) {
		jira.updateTicket(ticketKey, new BuildingLambda<IssueUpdateBuilder>() {
			
			@Override
			public void build(IssueUpdateBuilder b) {
				b.removeAffectedVersion(varReplacer.replace(versionToRemove));
				
			}
		});
	}
}
