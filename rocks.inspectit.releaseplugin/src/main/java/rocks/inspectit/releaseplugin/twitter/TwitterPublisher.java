package rocks.inspectit.releaseplugin.twitter;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.ItemGroup;
import hudson.model.ParameterValue;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.ParametersAction;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import jenkins.model.Jenkins;

import org.apache.commons.lang.text.StrSubstitutor;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.conf.ConfigurationBuilder;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;


/**
 * Plugin allowing to add and modify an arbitrary number of Tickets.
 * 
 * @author Jonas Kunz
 *
 */
public class TwitterPublisher extends Builder {


	private String tokenCredentialsID;
	
	private String consumerCredentialsID;
	
	private String tweetText;
	
	
	@DataBoundConstructor
	public TwitterPublisher(String tokenCredentialsID,
			String consumerCredentialsID, String tweetText) {
		super();
		this.tokenCredentialsID = tokenCredentialsID;
		this.consumerCredentialsID = consumerCredentialsID;
		this.tweetText = tweetText;
	}

	public String getTokenCredentialsID() {
		return tokenCredentialsID;
	}

	public String getConsumerCredentialsID() {
		return consumerCredentialsID;
	}	

	public String getTweetText() {
		return tweetText;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

		StrSubstitutor varReplacer = getVariablesSubstitutor(build, listener);
		PrintStream logger = listener.getLogger();
		
		UsernamePasswordCredentials tokenCred = CredentialsMatchers.firstOrNull(
                com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, Jenkins.getInstance(), ACL.SYSTEM),
                CredentialsMatchers.withId(tokenCredentialsID)
        );

		UsernamePasswordCredentials consumerCred = CredentialsMatchers.firstOrNull(
                com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, Jenkins.getInstance(), ACL.SYSTEM),
                CredentialsMatchers.withId(consumerCredentialsID)
        );
		
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setDebugEnabled(true)
		  .setOAuthConsumerKey(consumerCred.getUsername())
		  .setOAuthConsumerSecret(consumerCred.getPassword().getPlainText())
		  .setOAuthAccessToken(tokenCred.getUsername())
		  .setOAuthAccessTokenSecret(tokenCred.getPassword().getPlainText());
		TwitterFactory tf = new TwitterFactory(cb.build());
		Twitter twitter = tf.getInstance();
        
        String text =varReplacer.replace(this.tweetText);
        
        try {
			for(Status stat : twitter.getUserTimeline()) {
				if(stat.getText().equalsIgnoreCase(text)) {
					logger.println("Tweet is already present, therefore skipping publishing.");
					return true;
				}
			}
			logger.println("Publishing tweet.");
	        twitter.updateStatus(text);
		} catch (TwitterException e) {
			throw new RuntimeException("Could not itneract with twitter.", e);
		}
        


		return true;
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
	 * The descriptor class.
	 * @author JKU
	 *
	 */
	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

	
		/**
		 * Constructor.
		 */
		public DescriptorImpl() {
			super();
			load();
		}

		@Override
		public String getDisplayName() {
			return "Twitter Publisher";
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}
		
		
		@SuppressWarnings("deprecation")
		public ListBoxModel doFillTokenCredentialsIDItems(@AncestorInPath ItemGroup<?> context) {
            if (!(context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance()).hasPermission(Computer.CONFIGURE)) {
                return new ListBoxModel();
            }
            return new StandardListBoxModel().withAll(
                    CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, context, ACL.SYSTEM));
                            
        }

		
		@SuppressWarnings("deprecation")
		public ListBoxModel doFillConsumerCredentialsIDItems(@AncestorInPath ItemGroup<?> context) {
            if (!(context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance()).hasPermission(Computer.CONFIGURE)) {
                return new ListBoxModel();
            }
            return new StandardListBoxModel().withAll(
                    CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, context, ACL.SYSTEM));
                            
        }

	}
}
