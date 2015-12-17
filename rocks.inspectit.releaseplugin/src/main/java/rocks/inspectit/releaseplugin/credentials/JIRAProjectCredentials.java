package rocks.inspectit.releaseplugin.credentials;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import hudson.Extension;
import hudson.security.ACL;
import hudson.util.FormValidation;

import javax.annotation.Nonnull;

import jenkins.model.Jenkins;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.NameWith;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;


/**
 * Class representing the credentials for accessing a specific JIA project via the Rest api.
 * Therefore, it consists out of the jira url, a username and a password and the key of the project.
 * 
 * @author Jonas Kunz
 *
 */
@NameWith(JIRAProjectCredentials.NameProvider.class)
public class JIRAProjectCredentials extends BaseStandardCredentials implements StandardCredentials {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * The JIRA URL.
	 */
	@Nonnull
	private final String url;
	/**
	 * The username.
	 */
	@Nonnull
	private final String urlUsername;
	/**
	 * The password.
	 */
	@Nonnull
	private final String urlPassword;
	/**
	 * The key of the project.
	 */
	@Nonnull
	private final String projectKey;
	
	/**
	 * Databound constructor, usually called by jenkins.
	 * 
	 * @param scope the scope
	 * @param id the id
	 * @param description the description
	 * @param url the JIRA url
	 * @param urlUsername the user used to access jira
	 * @param urlPassword the password
	 * @param projectKey the key of the jira project.
	 */
	@DataBoundConstructor
	public JIRAProjectCredentials(CredentialsScope scope, String id, String description, String url, String urlUsername, String urlPassword, String projectKey) {
		super(scope, id, description);
		this.url = url;
		this.urlUsername = urlUsername;
		this.urlPassword = urlPassword;
		this.projectKey = projectKey;
	}

	public String getUrl() {
		return url;
	}

	public String getUrlUsername() {
		return urlUsername;
	}

	public String getUrlPassword() {
		return urlPassword;
	}	
	
	public String getProjectKey() {
		return projectKey;
	}



	/**
	 * Descriptor class.
	 * @author JKU
	 *
	 */
	@Extension public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        @Override public String getDisplayName() {
            return "JIRA Project with credentials";
        }
        

        /**
         * 
         * doCheck method evaluating whether the connection details are correct.
         * 
         * @param value the url
         * @param projectKey the username 
         * @param urlUsername the username 
         * @param urlPassword the password
         * @return a message, saying whether the details are correct or not (and possibly why)
         */
        public FormValidation doCheckUrl(@QueryParameter String value, @QueryParameter String projectKey, @QueryParameter String urlUsername, @QueryParameter String urlPassword) {
        	
        	if (value == null || value.isEmpty()) {
        		return FormValidation.warning("Please specify an URL");
        	}
        	if (projectKey == null || projectKey.isEmpty()) {
        		return FormValidation.warning("Please specify an Project Key");
        	}
        	if (urlUsername == null || urlUsername.isEmpty()) {
        		return FormValidation.warning("Please specify an username");
        	}
        	if (urlPassword == null || urlPassword.isEmpty()) {
        		return FormValidation.warning("Please specify an password");
        	}
        	String message = testJiraProjectConnection(value, projectKey, urlUsername, urlPassword);
        	if (message == null) {
            	return FormValidation.ok("URL is accessible with the given credentials");
        	} else {
            	return FormValidation.warning(message);
        		
        	}
        }
        
        
        /**
         * 
         * Tries to fetch the given project using the credentials.
         * 
         * @param url the url to try
         * @param projectKey the key of the project to check
         * @param user the username
         * @param password the password
         * @return
         * 	null if the data is allright, an error message otherwise.
         */
        private String testJiraProjectConnection(String url, String projectKey, String user, String password) {
    		CredentialsProvider credsProvider = new BasicCredentialsProvider();
    		credsProvider.setCredentials(AuthScope.ANY,
    				new UsernamePasswordCredentials(user, password));
    		CloseableHttpClient httpclient = HttpClients.custom()
    				.setDefaultCredentialsProvider(credsProvider).build();

    		

    		URI uri;
    		try {
    			uri = new URIBuilder(url + "/rest/api/2/project/" + projectKey).build();
    		} catch (URISyntaxException e) {
    			return "Malformed URL: " + e.getMessage();    			
    		}
    		
    		if (uri.getScheme() == null || uri.getHost() == null) {
    			return "Malformed URL";
    		}
    		
    		// Create AuthCache instance
    		AuthCache authCache = new BasicAuthCache();
    		// Generate BASIC scheme object and add it to the local auth cache
    		BasicScheme basicAuth = new BasicScheme();
    		HttpHost host = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
    		authCache.put(host, basicAuth);

    		// Add AuthCache to the execution context
    		HttpClientContext context = HttpClientContext.create();
    		context.setCredentialsProvider(credsProvider);
    		context.setAuthCache(authCache);

    		
    		HttpGet httpGet = new HttpGet(uri);

    		try {
    			HttpResponse response = httpclient.execute(httpGet, context);
    			httpclient.close();
    			if (response.getStatusLine().getStatusCode() == 200) {
    				return null;
    			} else {
    				return "Invalid Credentials, Project Key or URL: " + response.getStatusLine();
    				
    			}
    		} catch (IOException e) {
    			return "Could not access the REST API under the given url: " + e.getClass().getName() + " - " + e.getMessage();
    		} 
    	}

    }

	
	/**
	 * Utility method for translating an id into an actual credentials object.
	 * @param id
	 * 		the id of the credentials to query
	 * @return
	 * 		the obejct representing these credentials or null, if is not a valid id.
	 */
	@SuppressWarnings("deprecation")
	public static JIRAProjectCredentials getByID(String id) {
		return CredentialsMatchers.firstOrNull(
                com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(JIRAProjectCredentials.class, Jenkins.getInstance(), ACL.SYSTEM),
                CredentialsMatchers.withId(id)
        );
	}
	

	/**
	 * The name-provider, used to print the name of the credentials into the combobox.
	 * @author JKU
	 *
	 */
	public static class NameProvider extends CredentialsNameProvider<JIRAProjectCredentials> {

        @Override public String getName(JIRAProjectCredentials c) {
            return c.getUrl() + " (" + c.getProjectKey() + ") - " + c.getUrlUsername();
        }
        
	}
	
}
