package rocks.inspectit.releaseplugin;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;

import com.atlassian.jira.rest.client.api.AuthenticationHandler;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * Wrapper class around the apache http client, allows sending and recieving requests / responses in JSON format. 
 * 
 * @author Jonas Kunz
 *
 */
public class JsonHTTPClientWrapper {

	/**
	 * The url of the server to access.
	 */
	private String url;
	/**
	 * The username used for access.
	 */
	private String user;
	/**
	 * The password used for access.
	 */
	private String password;
	
	private String proxy;
	/**
	 * the underlying http client.
	 */
	private CloseableHttpClient client;
	/**
	 * The credentialsprovider for the connection.
	 */
	private CredentialsProvider credsProvider;

	
	/**
	 * Initializes a new connection with the given connection information.
	 * @param url the url of the server
	 * @param user the username
	 * @param password the password
	 */
	public JsonHTTPClientWrapper(String url, String user, String password, String proxy) {
		this.url = url;
		this.password = password;
		this.user = user;
		this.proxy = proxy;
		connect();
	}
	

	/**
	 * private method for creating the connection.
	 */
	private void connect() {
		credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(
				AuthScope.ANY,
				new UsernamePasswordCredentials(user, password));
		HttpClientBuilder clientFactory = HttpClients.custom();
		if(proxy != null) {
			clientFactory.setProxy(HttpHost.create(proxy));
		}
		client = clientFactory.setDefaultCredentialsProvider(credsProvider).build();

	}

	
	
	public String getProxy() {
		return proxy;
	}


	/**
	 * closes the connection.
	 */
	public void destroy() {
		try {
			client.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Executes the given request, throws an error if the status is any
	 * different from 200.
	 * 
	 * @param request the request to execute.
	 * @return the response parsed into a JSON element
	 */
	private JsonElement executeRequest(HttpUriRequest request) {
		try {
			java.net.URI uri = request.getURI();
			AuthCache authCache = new BasicAuthCache();
			BasicScheme basicAuth = new BasicScheme();
			HttpHost host = new HttpHost(uri.getHost(), uri.getPort(),
					uri.getScheme());
			authCache.put(host, basicAuth);

			HttpClientContext context = HttpClientContext.create();
			context.setCredentialsProvider(credsProvider);
			context.setAuthCache(authCache);

			HttpResponse response = client.execute(request, context);
			String jsonResponse = new BasicResponseHandler()
					.handleResponse(response);
			if (jsonResponse != null) {
				return new JsonParser().parse(jsonResponse);
			} else {
				return null;
			}

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Issues a put request, using the given data.
	 * 
	 * @param path
	 * 		the path (relative to the url) to issue the request on
	 * @param element
	 * 		the put-data
	 * @return
	 * 		null if the resposne was empty, an JSonElement representing the parsed response otherwise
	 */
	public JsonElement putJson(String path, JsonElement element) {
		URI requestTarget;
		try {
			requestTarget = new URIBuilder(url + path).build();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		HttpPut putReq = new HttpPut(requestTarget);
		putReq.addHeader("content-type", "application/json");
		putReq.addHeader("Accept", "application/json");
		try {
			putReq.setEntity(new StringEntity(new Gson().toJson(element)));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		return executeRequest(putReq);
	}
	
	/**
	 * Issues a post request, using the given data.
	 * 
	 * @param path
	 * 		the path (relative to the url) to issue the request on
	 * @param element
	 * 		the post-data
	 * @return
	 * 		null if the response was empty, an JSonElement representing the parsed response otherwise
	 */
	public JsonElement postJson(String path, JsonElement element) {
		URI requestTarget;
		try {
			requestTarget = new URIBuilder(url + path).build();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		HttpPost postReq = new HttpPost(requestTarget);
		postReq.addHeader("content-type", "application/json");
		postReq.addHeader("Accept", "application/json");
		try {
			postReq.setEntity(new StringEntity(new Gson().toJson(element)));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		return executeRequest(postReq);
	}
	
	
	/**
	 * Issues a get request.
	 * 
	 * @param path
	 * 		the path (relative to the url) to issue the request on
	 * @return
	 * 		null if the response was empty, an JSonElement representing the parsed response otherwise
	 */
	public JsonElement getJson(String path) {
		return getJson(path, new HashMap<String, String>());
	}

	/**
	 * Issues a get request, urlencoding the given parameters.
	 * 
	 * @param path
	 * 		the path (relative to the url) to issue the request on
	 * @param parameters
	 * 		a map where the keys are paramternames and the values are the values.
	 * @return
	 * 		null if the response was empty, an JSonElement representing the parsed response otherwise
	 */
	public JsonElement getJson(String path, Map<String, String> parameters) {
		URI requestTarget;
		try {
			URIBuilder builder = new URIBuilder(url + path);
			for (Entry<String, String> e: parameters.entrySet()) {
				builder.addParameter(e.getKey(), e.getValue());
			}
			requestTarget = builder.build();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		HttpGet getReq = new HttpGet(requestTarget);
		return executeRequest(getReq);
	}


	public CloseableHttpClient getHttpClient() {
		return client;
	}

	
	
	
}
