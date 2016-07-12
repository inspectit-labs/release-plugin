package rocks.inspectit.releaseplugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/*
import java.util.function.Function;
import java.util.function.Supplier;
*/



import rocks.inspectit.releaseplugin.credentials.JIRAProjectCredentials;

/**
 * 
 * This class allows loading and caching of JIRA metadata, like the available ticket types, priorities and so on.
 * The typical use case is for filling in the suggestions of comboboxes in the UI.
 * this avoids refetching the data if more than one field needs it.
 * 
 * Data is cached per-credentials.
 * 
 * @author Jonas Kunz
 *
 */
public final class JIRAMetadataCache {
	
	/**
	 * Interface for functions, taken from Java 8 for backwards compatibility.
	 * @author JKU
	 *
	 * @param <P> parameter type
	 * @param <R> return value type
	 */
	public interface Function<P, R> {
		/**
		 * Apply the function.
		 * @param param the parameter
		 * @return the return value
		 */
		R apply(P param);
	}
	
	/**
	 * Interface for Supplies, taken from Java 8 for backwards compatibility.
	 * @author JKU
	 *
	 * @param <R> return value type
	 */
	public interface Supplier<R> {
		/**
		 * Apply the supplier.
		 * @return the return value
		 */
		R get();
	}
	
	/**
	 * key used for internal storage.
	 */
	private static final String ISSUE_TYPES = "ISSUE_TYPES";
	/**
	 * key used for internal storage.
	 */
	private static final String ISSUE_STATUSES = "ISSUE_STATUSES";
	/**
	 * key used for internal storage.
	 */
	private static final String ISSUE_PRIORITIES = "ISSUE_PRIORITITES";
	/**
	 * key used for internal storage.
	 */
	private static final String PROJECT_VERSIONS = "PROJECT_VERSIONS";
	

	/**
	 * key used for internal storage.
	 */
	private static final String FIELD_METADATA = "FIELD_METADATA";
	
	/**
	 * The actual cache.
	 */
	private ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> caches;
	
	/**
	 * contains the singleton instance.
	 */
	private static JIRAMetadataCache singleton;
	
	/**
	 * 
	 * @return the cache instance.
	 */
	public static JIRAMetadataCache getSingleton() {
		if (singleton == null) {
			synchronized (JIRAMetadataCache.class) {
				//check again for synchronization
				if (singleton == null) {
					singleton = new JIRAMetadataCache();				
				}			
			}
		}
		return singleton;
	}
	
	
	/**
	 * Constructor.
	 */
	private JIRAMetadataCache() {
		caches = new ConcurrentHashMap<String, ConcurrentHashMap<String, Object>>();
	}
	
	/**
	 * returns the available issue types. 
	 * @param credentialsID
	 * 		the id of the credentials of the JIRA project.
	 * @return
	 * 		a list with the names of the types.
	 */
	public List<String> getAvailableIssueTypes(final String credentialsID) {
		
		List<String> result = getCreateCacheEntry(credentialsID, ISSUE_TYPES, new Supplier<List<String>>() {
			@Override
			public List<String> get() {
				return unsafeExecuteJiraCommands(credentialsID, new Function<JIRAAccessTool, List<String>>() {
					@Override
					public List<String> apply(JIRAAccessTool jira) {
						return Collections.unmodifiableList(jira.getAvailableIssueTypes());
					}
					
				});
			}
		});
		return result == null ? new ArrayList<String>() : result;
	
	}
	
	/**
	 * returns the available issue statuses. 
	 * @param credentialsID
	 * 		the id of the credentials of the JIRA project.
	 * @return
	 * 		a list with the names of the statuses.
	 */
	public List<String> getAvailableIssueStatuses(final String credentialsID) {
		List<String> result = getCreateCacheEntry(credentialsID, ISSUE_STATUSES, new Supplier<List<String>>() {
			@Override
			public List<String> get() {
				return unsafeExecuteJiraCommands(credentialsID, new Function<JIRAAccessTool, List<String>>() {
					@Override
					public List<String> apply(JIRAAccessTool jira) {
						return Collections.unmodifiableList(jira.getAvailableIssueStatuses());
					}
					
				});
			}
		});
		return result == null ? new ArrayList<String>() : result;
	}
	
	
	/**
	 * returns the available issue priorities. 
	 * @param credentialsID
	 * 		the id of the credentials of the JIRA project.
	 * @return
	 * 		a list with the names of the priorities.
	 */
	public List<String> getAvailableIssuePriorities(final String credentialsID) {
		List<String> result = getCreateCacheEntry(credentialsID, ISSUE_PRIORITIES, new Supplier<List<String>>() {
			@Override
			public List<String> get() {
				return unsafeExecuteJiraCommands(credentialsID, new Function<JIRAAccessTool, List<String>>() {
					@Override
					public List<String> apply(JIRAAccessTool jira) {
						return Collections.unmodifiableList(jira.getAvailableIssuePriorities());
					}
					
				});
			}
		});
		return result == null ? new ArrayList<String>() : result;
	}	
	
	
	/**
	 * returns the available versions of the project. 
	 * @param credentialsID
	 * 		the id of the credentials of the JIRA project.
	 * @return
	 * 		a list with the names of the versions.
	 */
	public List<String> getAvailableVersions(final String credentialsID) {
		
		List<String> result = getCreateCacheEntry(credentialsID, PROJECT_VERSIONS, new Supplier<List<String>>() {
			@Override
			public List<String> get() {
				return unsafeExecuteJiraCommands(credentialsID, new Function<JIRAAccessTool, List<String>>() {
					@Override
					public List<String> apply(JIRAAccessTool jira) {
						return Collections.unmodifiableList(jira.getAvailableVersions());
					}
					
				});
			}
		});
		return result == null ? new ArrayList<String>() : result;
		
	}

	
	/**
	 * returns the metadata of all available fields.
	 * @param credentialsID
	 * 		the id of the credentials of the JIRA project.
	 * @return
	 * 		a list with the names of the versions.
	 */
	public List<FieldMetadata> getFieldMetadata(final String credentialsID) {
		
		List<FieldMetadata> result = getCreateCacheEntry(credentialsID, FIELD_METADATA, new Supplier<List<FieldMetadata>>() {
			@Override
			public List<FieldMetadata> get() {
				return unsafeExecuteJiraCommands(credentialsID, new Function<JIRAAccessTool, List<FieldMetadata>>() {
					@Override
					public List<FieldMetadata> apply(JIRAAccessTool jira) {
						return Collections.unmodifiableList(jira.getAvailableFields());
					}
					
				});
			}
		});
		return result == null ? new ArrayList<FieldMetadata>() : result;
		
	}
	
	/**
	 * Private utility method which does the following:
	 * if the data with the given key is already present, it is returned.
	 * if the data is not present, the initialization lambda is executed and the data is stored in the cache.
	 * 
	 * This method ensures proper synchronization and makes sure that initialization is only executed once.
	 * 
	 * @param <T> the type of the entry data
	 * 
	 * @param credentialsID the id of the credentials, used to differentiate between different JIRA connections
	 * @param entryID the key of the entry to fetch (or create)
	 * @param initialization supplier used to initialize the value if it is not present.
	 * @return the data for the given credentials / entryID combo
	 */
	@SuppressWarnings("unchecked")
	private <T> T getCreateCacheEntry(String credentialsID, String entryID, Supplier<? extends T> initialization) {
		ConcurrentHashMap<String, Object> cache = caches.get(credentialsID);
		if (cache == null) {
			caches.putIfAbsent(credentialsID, new ConcurrentHashMap<String, Object>());
			cache = caches.get(credentialsID);
		}
		
		Object entry = cache.get(entryID);
		if (entry == null) {
			synchronized (cache) {
				entry = cache.get(entryID);
				if (entry == null) {
					entry = initialization.get();
					if (entry != null) {
						cache.put(entryID, entry);
					}
				}
			}
		}
		return (T) entry;
		
	}
	
	
	/**
	 * This method will open a connection to JIRA and try to execute the given commands.
	 * If something fails (an exception occurs), the exception is caught and null is returned instead.
	 * If the commands can be executed successfully, their result is returned.
	 *  
	 *  
	 * @param <T> the return value-type of the executed commands 
	 * @param credentialsID
	 * 	the ID of the credentials to access JIRA.
	 * @param commands
	 * 	the commands to execute
	 * @return
	 * 	the result of the commands or null if the execution was not successful.
	 */
	private <T> T unsafeExecuteJiraCommands(String credentialsID, Function<JIRAAccessTool, T> commands) {
		JIRAProjectCredentials cred = JIRAProjectCredentials.getByID(credentialsID);
		JIRAAccessTool jira = null;
    	try {
    		jira = new JIRAAccessTool(cred.getUrl(), cred.getUrlUsername(), cred.getUrlPassword(), null, cred.getProjectKey(), credentialsID);
    		return commands.apply(jira);
    	} catch (Exception e) {
    		return null;
    	} finally {
    		//safety first: make sure the connection gets closed.
    		if (jira != null) {
    			try {
    				jira.destroy();
    			} catch (Exception e) { }
    		}
    	}
	}
	
	
}
