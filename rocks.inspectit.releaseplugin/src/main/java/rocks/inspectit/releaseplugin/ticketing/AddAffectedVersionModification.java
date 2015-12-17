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
 * Modification selectable for tickets to add a version to the list of affected version.
 * 
 * @author Jonas Kunz
 *
 */
public class AddAffectedVersionModification extends TicketModification {

	/**
	 * The name of the version which wil lbe added.
	 */
	private String versionToAdd;
	
	
	/**
	 * Constructor.
	 * @param versionToAdd the version to add
	 */
	@DataBoundConstructor
	public AddAffectedVersionModification(String versionToAdd) {
		super();
		this.versionToAdd = versionToAdd;
	}

	public String getVersionToAdd() {
		return versionToAdd;
	}


	/**
	 * Descriptor implementation.
	 * @author JKU
	 *
	 */
	@Extension public static class DescribtorImpl extends Descriptor<TicketModification> {

		@Override
		public String getDisplayName() {
			return "Add Affected Version";
		}
		
	}


	@Override
	public void apply(String ticketKey, JIRAAccessTool jira, final StrSubstitutor varReplacer, PrintStream logger) {
		jira.updateTicket(ticketKey, new BuildingLambda<IssueUpdateBuilder>() {
			@Override
			public void build(IssueUpdateBuilder b) {
				b.addAffectedVersion(varReplacer.replace(versionToAdd));
			}
		});
	}
}
