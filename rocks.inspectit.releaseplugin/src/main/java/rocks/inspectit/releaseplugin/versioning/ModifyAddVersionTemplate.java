package rocks.inspectit.releaseplugin.versioning;

import java.io.PrintStream;







import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;







import org.apache.commons.lang.text.StrSubstitutor;
import org.joda.time.DateTime;
import org.kohsuke.stapler.DataBoundConstructor;







import com.atlassian.jira.rest.client.api.domain.input.VersionInputBuilder;

import rocks.inspectit.releaseplugin.JIRAAccessTool;
import rocks.inspectit.releaseplugin.JIRAAccessTool.BuildingLambda;

/**
 * 
 * Template for specifying the data needed for creating or updating JIRA versions.
 * 
 * @author Jonas Kunz
 *
 */
public class ModifyAddVersionTemplate extends AbstractDescribableImpl<ModifyAddVersionTemplate> {

	/**
	 * The name of the version to update or create.
	 */
	private String versionName;
	
	/**
	 * true if the description shall be replaced.
	 */
	private boolean replaceDescription;
	
	/**
	 * The new description text.
	 */
	private String descriptionText;
	/**
	 * can be released,unreleased or keep (defaults to unrelease for new tickets)
	 * "keep" will keep the current release state otherwise.
	 */
	private String releaseState;
	
	/**
	 * true if the build should fail if any ticket matches the jql given below.
	 * Can be used for example to make sure that there are no tickets still open for a versio nwhen releasing it.
	 */
	private boolean failOnJQL;

	
	/**
	 * the jql fail query.
	 */
	private String failQuery;
	
	/**
	 * Constructor, called by Jenkins.
	 * @param versionName the version to create or update.
	 * @param replaceDescription true if the description shall be replaced
	 * @param descriptionText the new description
	 * @param releaseState what to do with the release state? released, unreleased or keep
	 * @param failOnJQL fail if anything mathces the query
	 * @param failQuery the query
	 */
	@DataBoundConstructor
	public ModifyAddVersionTemplate(String versionName, boolean replaceDescription,
			String descriptionText, String releaseState, boolean failOnJQL,
			String failQuery) {
		super();
		this.versionName = versionName;
		this.replaceDescription = replaceDescription;
		this.descriptionText = descriptionText;
		this.releaseState = releaseState;
		this.failOnJQL = failOnJQL;
		this.failQuery = failQuery;
	}
	
	public String getVersionName() {
		return versionName;
	}
	
	public boolean isReplaceDescription() {
		return replaceDescription;
	}

	public String getDescriptionText() {
		return descriptionText;
	}

	public String getReleaseState() {
		return releaseState;
	}

	public boolean isFailOnJQL() {
		return failOnJQL;
	}

	public String getFailQuery() {
		return failQuery;
	}
	
	/**
	 * Creates or updates the version this template targets.
	 * 
	 * @param jira
	 * 		the jira to use
	 * @param varReplacer
	 * 		variable replace to enter build parameters
	 * @param logger
	 * 		the logger used for outputing information
	 */
	public void applyModifications(final JIRAAccessTool jira, StrSubstitutor varReplacer, PrintStream logger) {
		
		
		final String versionName = varReplacer.replace(this.versionName);
		final String descriptionText = varReplacer.replace(this.descriptionText);
		final String failQuery = varReplacer.replace(this.failQuery);
		
		logger.println("Updating / Creating version " + versionName);
		
		jira.createUpdateVersion(versionName, new BuildingLambda<VersionInputBuilder>() {
			@Override
			public void build(VersionInputBuilder b) {
				if (replaceDescription) {
					b.setDescription(descriptionText);
				}
				if (releaseState.equals("unreleased")) {
					b.setReleased(false);
				} else if (releaseState.equals("released")) {
					b.setReleased(true);
					b.setReleaseDate(new DateTime());
					if (failOnJQL) {
						String jql = "affectedVersion=\"" + versionName + "\" AND (" + failQuery + ")";
						long numberOfOpenTickets = jira.getTicketsByJQL(jql).stream().count();
						if (numberOfOpenTickets > 0) {
							throw new RuntimeException("Unable to release version " + versionName + ", because there are still "
									+ numberOfOpenTickets + " matching the query '" + jql + "'");
						}
					}
				}
			}
		});
	}
	

	/**
	 * Descriptor class.
	 * @author JKU
	 *
	 */
	@Extension public static class DescribtorImpl extends Descriptor<ModifyAddVersionTemplate> {

		@Override
		public String getDisplayName() {
			return "Add / Modify JIRA Project Version";
		}
		
	}
	

}
