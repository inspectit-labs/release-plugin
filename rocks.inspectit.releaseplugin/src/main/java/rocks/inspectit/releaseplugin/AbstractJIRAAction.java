package rocks.inspectit.releaseplugin;

import hudson.EnvVars;
import hudson.model.Action;
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

import java.util.HashMap;
import java.util.Map;

import jenkins.model.Jenkins;

import org.apache.commons.lang.text.StrSubstitutor;
import org.kohsuke.stapler.AncestorInPath;

import rocks.inspectit.releaseplugin.credentials.JIRAProjectCredentials;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;

/**
 * 
 * Parent class for build steps which need an access to Jira.
 * 
 * @author Jonas Kunz
 *
 */
public abstract class AbstractJIRAAction extends Builder {

	/**
	 * represents an internal id of the chosen credentials to access JIRA.
	 */
	private String jiraCredentialsID;
	
	/**
	 * Constructor.
	 * @param jiraCredentialsID the id of the jira credentials which will be used
	 */
	public AbstractJIRAAction(String jiraCredentialsID) {
		this.jiraCredentialsID = jiraCredentialsID;
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
				if (val.getValue() != null) {
					variables.put(val.getName(), val.getValue().toString());
				}
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
	 * The descriptor-class.
	 * 
	 * @author JKU
	 */
	public abstract static class DescriptorImpl extends BuildStepDescriptor<Builder> {
		
		
			@Override
			public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
				return true;
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
	
	}
	





}
