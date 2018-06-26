package rocks.inspectit.releaseplugin.releasenotes;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.ItemGroup;
import hudson.model.ParameterValue;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.ParametersAction;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jenkins.model.Jenkins;

import org.apache.commons.lang.text.StrSubstitutor;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHReleaseBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import rocks.inspectit.releaseplugin.JIRAAccessTool;
import rocks.inspectit.releaseplugin.credentials.JIRAProjectCredentials;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.cloudbees.jenkins.GitHubRepositoryName;
import com.cloudbees.jenkins.GitHubRepositoryNameContributor;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;

/**
 * 
 * Tool for publishing a list of tickets on a confluence page.
 * 
 * @author Jonas Kunz
 *
 */
public class GithubReleasePublisher extends Notifier {
	

	/**
	 * represents an internal id of the chosen credentials to access JIRA.
	 */
	private String jiraCredentialsID;
	
	/**
	 * The JQL filter of the tickets to list.
	 */
	private String jqlFilter;
	/**
	 * Title of the page to publish.
	 */
	private String repoName;

	/**
	 * The tag of the release.
	 */
	private String releaseTag;
	
	/**
	 * The name of the release.
	 */
	private String releaseName;

	/**
	 * publish it as prerelease.
	 */
	private boolean isPrerelease;
	
	/**
	 * commas-seperated file patterns for the files to upload.
	 */
	private String artifactPatterns;
	

	


	


	/**
	 * Data bound constructor.
	 * @param jiraCredentialsID the jira credentials
	 * @param jqlFilter the jql fitler
	 * @param repoName the reponame
	 * @param releaseTag the tag
	 * @param releaseName the name
	 * @param isPrerelease prerelease flag
	 * @param artifactPatterns name patterns
	 */
	@DataBoundConstructor
	public GithubReleasePublisher(String jiraCredentialsID, String jqlFilter,
			String repoName, String releaseTag, String releaseName, boolean isPrerelease,
			String artifactPatterns) {
		this.jiraCredentialsID = jiraCredentialsID;
		this.jqlFilter = jqlFilter;
		this.repoName = repoName;
		this.releaseTag = releaseTag;
		this.isPrerelease = isPrerelease;
		this.artifactPatterns = artifactPatterns;
		this.releaseName = releaseName;
	}

	



	public String getJqlFilter() {
		return jqlFilter;
	}

	public String getRepoName() {
		return repoName;
	}

	public String getReleaseTag() {
		return releaseTag;
	}

	public String getReleaseName() {
		return releaseName;
	}

	public boolean getIsPrerelease() {
		return isPrerelease;
	}

	public String getArtifactPatterns() {
		return artifactPatterns;
	}





	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {

		
		StrSubstitutor varReplacer = getVariablesSubstitutor(build, listener);
		final PrintStream logger = listener.getLogger();

		JIRAProjectCredentials jiraCred = getJiraCredentials();
		
		JIRAAccessTool jira = new JIRAAccessTool(jiraCred.getUrl(), jiraCred.getUrlUsername(), jiraCred.getUrlPassword(), null, jiraCred.getProjectKey(), getJiraCredentialsID());
		
		String jqlFilter = varReplacer.replace(this.jqlFilter);
		String repoName = varReplacer.replace(this.repoName);
		String artifactPatterns = varReplacer.replace(this.artifactPatterns);
		String releaseTag = varReplacer.replace(this.releaseTag);
		String releaseName = varReplacer.replace(this.releaseName);
		
		GHRepository repo = null;
		GitHubRepositoryName repositoryName = null;
		
		for (GitHubRepositoryName name : GitHubRepositoryNameContributor.parseAssociatedNames((Job<?, ?>) build.getProject())) {
		    for (GHRepository repository : name.resolve()) {
		    	if (repoName.equalsIgnoreCase(repository.getFullName())) {
		    		repositoryName = name;
		    		repo = repository;
		    	}
		    }
		}
		
		if (repo == null) {
			throw new RuntimeException("Repository with name " + repoName + " was not found! did you specify the orrect credentials under the git section?");
		} else {		
			

			List<Issue> tickets = jira.getTicketsByJQL(jqlFilter);
			logger.println("Found " + tickets.size() + " tickets assigned to GitHub release " + releaseName + ".");
			String pageHTML = "";
			if (!tickets.isEmpty()) {
				pageHTML = jira.buildReleaseNotesHTML(tickets);
			}
			
			
			
			GHReleaseBuilder releaseBuilder = repo.createRelease(releaseTag);
			releaseBuilder.name(releaseName);
			releaseBuilder.prerelease(isPrerelease);
			releaseBuilder.body(pageHTML);
			
			GHSerializableConnection ghConnection = new GHSerializableConnection(repositoryName);
			
			final GHRelease rel = releaseBuilder.create();
			
			for (FilePath path : build.getWorkspace().list(artifactPatterns)) {

				String assetName = path.getName();
				
				boolean uploadSuccessful = false;
				int numberOfTrialsLeft = 3;
				do {					
					try {
						try {
							//sleep a while to prevent issues if the assets are uploaded from different machines
							Thread.sleep(1000);
						} catch (Exception e3) { }
						numberOfTrialsLeft--;
						logger.println("Uploading asset to release: " + assetName);
						path.act(new GHReleaseFileCallable(ghConnection, releaseName));	
						uploadSuccessful = true;
					} catch (Exception e) {
						logger.println("Asset upload failed (" + e.getClass().getName() + ": " + e.getMessage() + ")");
						try {
							//here a very long sleep is required as otherwise we run into issues with the asset list
							//being not up-to-date
							Thread.sleep(60000);
						} catch (Exception e3) { }
						//try deleting the failed asset if it still was created
						for (GHAsset asset : rel.getAssets()) {
							if (asset.getName().equals(assetName)) {
								logger.println("Deleting invalid asset from release (ID: " + asset.getId() + ").");	
								try {
									asset.delete();
								} catch (Exception e2) {
									logger.println("Could not delete asset, continuing anyway.. (" + e2.getClass().getName() + ": " + e2.getMessage() + ")");										
								}
							}
						}
						try {
							Thread.sleep(5000);
						} catch (Exception e3) { }
						if (numberOfTrialsLeft > 0) {
							logger.println("Retrying upload " + numberOfTrialsLeft + " times.");						
						} else {
							logger.println("Aborting build due to upload failure.");
							//build failure in case that we tried to often
							throw e;
						}
					}
				} while (!uploadSuccessful && numberOfTrialsLeft > 0);
			}
		}
		
		jira.destroy();

		return true;
	}

	
	
	
	
	/**
	 * @return the ID representing the credentials to access JIRA
	 */
	public String getJiraCredentialsID() {
		return jiraCredentialsID;
	}


	/**
	 * Translates the credentialsID into the actual credentials object.
	 * @return
	 * 		the credentials or null if none where chosen.
	 */
	public JIRAProjectCredentials getJiraCredentials() {
		return JIRAProjectCredentials.getByID(jiraCredentialsID);
	}




	@Override
	public Action getProjectAction(AbstractProject<?, ?> project) {
		return null;
	}

	/**
	 * Returns a StringSubstitutor replacing variables e.g. ${varName} with
	 * their content.
	 * 
	 * Considers build parameters AND environment variables, while giving priority to the build parameters.
	 * 
	 * @param build
	 *            the current build
	 * @param lis
	 *            the listener of the current build
	 * @return a string substitutor replacing all variables with their content.
	 */
	protected StrSubstitutor getVariablesSubstitutor(AbstractBuild<?, ?> build, BuildListener lis) {
		ParametersAction params = build.getAction(ParametersAction.class);
		Map<String, String> variables = new HashMap<String, String>();
		EnvVars env;
		try {
			env = build.getEnvironment(lis);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	  	for (Map.Entry<String, String> en : env.entrySet()) {
	  		variables.put(en.getKey(), en.getValue());
	  	}
		if (params != null) {
			for (ParameterValue val : params.getParameters()) {
				variables.put(val.getName(), val.getValue().toString());
			}
		}
		StrSubstitutor subs = new StrSubstitutor(variables);
		return subs;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.BUILD;
	}



	/**
	 * Uploads a file as an asset to a certain github release.
	 * @author Jonas Kunz
	 */
	private static final class GHReleaseFileCallable implements
			FileCallable<Void> {
		
		
		GHSerializableConnection ghConnection;

		/**
		 * the release the file will be uploaded to.
		 */
		private final String releaseName;
		
		/**
		 * Serialization UID.
		 */
		private static final long serialVersionUID = 1L;

		/**
		 * Constructor.
		 * @param host the repo host
		 * @param username the repo owner
		 * @param repoName the name of the repo
		 * @param releaseName the name of the release
		 */
		private GHReleaseFileCallable(GHSerializableConnection ghConnection, String releaseName) {
			this.releaseName = releaseName;
			this.ghConnection = ghConnection;
		}

		@Override
		public void checkRoles(RoleChecker checker) throws SecurityException {
			// TODO Auto-generated method stub
		}

		@Override
		public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
			//connect to the repository fro mthis node
			
			GHRelease releaseToUploadTo = null;
			
			GitHub github = ghConnection.connect();
			GHRepository repo = github.getRepository(ghConnection.getTotalRepositoryName());
			
			PagedIterable<GHRelease> listReleases = repo.listReleases();
			for (GHRelease release : listReleases) {
				if (release.getName().equals(releaseName)) {
					releaseToUploadTo = release;
				}
			}
			
			String mimeType = Files.probeContentType(f.toPath());
			//if the mime type cannot be determined, use plain/text
			//System.out.println(mimeType);
			if (mimeType == null) {
				mimeType = "text/plain";
			}
			releaseToUploadTo.uploadAsset(f, mimeType);
			
			return null;
		}
	}



	/**
	 * Descriptor class.
	 * @author JKU
	 *
	 */
	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		/**
		 * Constructor.
		 */
		public DescriptorImpl() {
			super();
			load();
		}

		@Override
		public String getDisplayName() {
			return "Publish A Release on GitHub";
		}
		
		/**
		 * Method for filling the credentials listbox with the available JIRA credentials.
		 * @param context the context
		 * @return a ListBoxModel containing all available credentials.
		 */
		//the method lookupCredentials is actually not deprecated, it has been replaced with
		//a same signature method with a variable number of arguments.
		@SuppressWarnings("deprecation")
		public ListBoxModel doFillJiraCredentialsIDItems(@AncestorInPath ItemGroup<?> context) {
            if (!(context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance()).hasPermission(Computer.CONFIGURE)) {
                return new ListBoxModel();
            }
            return new StandardListBoxModel().withAll(
                    CredentialsProvider.lookupCredentials(JIRAProjectCredentials.class, context, ACL.SYSTEM));
                            
        }

		@SuppressWarnings("rawtypes")
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

	}

}
