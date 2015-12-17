package rocks.inspectit.releaseplugin.credentials;

import hudson.Extension;
import hudson.security.ACL;
import hudson.util.FormValidation;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

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
 * Class representing the credentials for accessing confluence via the Rest api.
 * Therefore, it consists out of the confluence url, a username and a password.
 * 
 * @author Jonas Kunz
 *
 */
@NameWith(ConfluenceCredentials.NameProvider.class)
public class ConfluenceCredentials extends BaseStandardCredentials implements StandardCredentials {

	/**
	 * 
	 */
	private static final long serialVersionUID = -565781284221184319L;
	
	
	/**
	 * the confluence URL.
	 */
	@Nonnull
	private final String url;
	/**
	 * the username.
	 */
	@Nonnull
	private final String urlUsername;
	/**
	 * the password.
	 */
	@Nonnull
	private final String urlPassword;
	
	/**
	 * Constructor, usually only called by Jenkins.
	 * @param scope the scope
	 * @param id the id
	 * @param description the description
	 * @param url the confluence url
	 * @param urlUsername the username used for confluence
	 * @param urlPassword the password used for confluence
	 */
	@DataBoundConstructor
	public ConfluenceCredentials(CredentialsScope scope, String id, String description, String url, String urlUsername, String urlPassword) {
		super(scope, id, description);
		this.url = url;
		this.urlUsername = urlUsername;
		this.urlPassword = urlPassword;
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
	
	/**
	 * Descriptor class.
	 * @author JKU
	 *
	 */
	@Extension public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        @Override public String getDisplayName() {
            return "Confluence URL with credentials";
        }
        

        /**
         * 
         * doCheck method evaluating whether the connection details are correct.
         * 
         * @param value the url
         * @param urlUsername the username 
         * @param urlPassword the password
         * @return a message, saying whether the details are correct or not (and possibly why)
         */
        public FormValidation doCheckUrl(@QueryParameter String value, @QueryParameter String urlUsername, @QueryParameter String urlPassword) {
        	
        	if (value == null || value.isEmpty()) {
        		return FormValidation.warning("Please specify an URL");
        	}
        	if (urlUsername == null || urlUsername.isEmpty()) {
        		return FormValidation.warning("Please specify an username");
        	}
        	if (urlPassword == null || urlPassword.isEmpty()) {
        		return FormValidation.warning("Please specify an password");
        	}
        	String message = testConfluenceConnection(value, urlUsername, urlPassword);
        	if (message == null) {
            	return FormValidation.ok("URL is accessible with the given credentials");
        	} else {
            	return FormValidation.warning(message);
        		
        	}
        }
        
        /**
         * 
         * Tries to establish a connection to confluence using the given data.
         * Returns an error message if the connection could not be established
         * 
         * @param url confluence url
         * @param user the username for the given url
         * @param password the password
         * @return
         * 		null, if the connection could be established, an error message otherwise.
         */
        private String testConfluenceConnection(String url, String user, String password) {
    		CredentialsProvider credsProvider = new BasicCredentialsProvider();
    		credsProvider.setCredentials(AuthScope.ANY,
    				new UsernamePasswordCredentials(user, password));
    		CloseableHttpClient httpclient = HttpClients.custom()
    				.setDefaultCredentialsProvider(credsProvider).build();

    		

    		URI uri;
    		try {
    			//perform a fictional query
    			uri = new URIBuilder(url + "/rest/api/space?spaceKey=rdsfsdfsdf").build();
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
    				return "Invalid Credentials or URL: " + response.getStatusLine();
    				
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
	public static ConfluenceCredentials getByID(String id) {
		return CredentialsMatchers.firstOrNull(
                com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(ConfluenceCredentials.class, Jenkins.getInstance(), ACL.SYSTEM),
                CredentialsMatchers.withId(id)
        );
	}
	

	/**
	 * 
	 * Used to build the names shown in the dropdown list when choosing credentials.
	 * 
	 * @author Jonas Kunz
	 *
	 */
	public static class NameProvider extends CredentialsNameProvider<ConfluenceCredentials> {

        @Override public String getName(ConfluenceCredentials c) {
            return c.getUrl() + " - " + c.getUrlUsername();
        }

    }
	
}
