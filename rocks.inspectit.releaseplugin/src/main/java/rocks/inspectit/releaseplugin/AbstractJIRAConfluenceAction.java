package rocks.inspectit.releaseplugin;

import hudson.model.ItemGroup;
import hudson.model.Computer;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

import org.kohsuke.stapler.AncestorInPath;

import rocks.inspectit.releaseplugin.credentials.ConfluenceCredentials;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
/**
 * 
 * Parent class for build steps which need an access to Jira and Confluence.
 * 
 * @author Jonas Kunz
 *
 */
public abstract class AbstractJIRAConfluenceAction extends AbstractJIRAAction {
	
	/**
	 * represents an internal id of the chosen credentials to access Confluence.
	 */
	private String confluenceCredentialsID;
	

	
	
	/**
	 * Constructor.
	 * @param jiraCredentialsID the id of the JIRA credentials
	 * @param confluenceCredentialsID the id of the Confluence credentials
	 */
	public AbstractJIRAConfluenceAction(String jiraCredentialsID, String confluenceCredentialsID) {
		super(jiraCredentialsID);
		this.confluenceCredentialsID = confluenceCredentialsID;
	}


	public String getConfluenceCredentialsID() {
		return confluenceCredentialsID;
	}


	/**
	 * @return the chosen confluence credentials or null, if none where chosen
	 */
	public ConfluenceCredentials getConfluenceCredentials() {
		return ConfluenceCredentials.getByID(confluenceCredentialsID);
	}
	
	/**
	 * Descriptor class.
	 * @author JKU
	 */
	public abstract static class DescriptorImpl extends AbstractJIRAAction.DescriptorImpl {
		
				
			/**
			 * Method for filling the credentials listbox with the available Confluence credentials.
			 * @param context the context
			 * @return a ListBoxModel containing all available credentials
			 */
			//the method lookupCredentials is actually not deprecated, it has been replaced with
			//a same signature method with a variable number of arguments.
			@SuppressWarnings("deprecation")
			public ListBoxModel doFillConfluenceCredentialsIDItems(@AncestorInPath ItemGroup<?> context) {
	            if (!(context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance()).hasPermission(Computer.CONFIGURE)) {
	                return new ListBoxModel();
	            }
	            return new StandardListBoxModel().withAll(
	                    CredentialsProvider.lookupCredentials(ConfluenceCredentials.class, context, ACL.SYSTEM));
	                            
	        }
	
	}
	





}
