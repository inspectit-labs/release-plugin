package rocks.inspectit.releaseplugin.influxdb;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jenkins.model.Jenkins;

import org.apache.commons.lang.text.StrSubstitutor;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDB.ConsistencyLevel;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import rocks.inspectit.releaseplugin.influxdb.InfluxContentParser.ContentLine;

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
public class InfluxDBPublisher extends Builder {


	private String userCredentialsID;
		
	private String dbUrl;
	
	private String dbName;
	
	private String content;
	
	
	
	
	
	@DataBoundConstructor
	public InfluxDBPublisher(String userCredentialsID, String dbUrl,
			String dbName, String content) {
		super();
		this.userCredentialsID = userCredentialsID;
		this.dbUrl = dbUrl;
		this.dbName = dbName;
		this.content = content;
	}
	
	

	public String getUserCredentialsID() {
		return userCredentialsID;
	}



	public String getDbUrl() {
		return dbUrl;
	}



	public String getDbName() {
		return dbName;
	}



	public String getContent() {
		return content;
	}



	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

		StrSubstitutor varReplacer = getVariablesSubstitutor(build, listener);
		PrintStream logger = listener.getLogger();
		
		UsernamePasswordCredentials cred = CredentialsMatchers.firstOrNull(
                com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, Jenkins.getInstance(), ACL.SYSTEM),
                CredentialsMatchers.withId(userCredentialsID)
        );

		String dbUrl = varReplacer.replace(this.dbUrl);
		String dbName = varReplacer.replace(this.dbName);
		String content = varReplacer.replace(this.content);

		InfluxDB influx = InfluxDBFactory.connect(dbUrl, cred.getUsername(), cred.getPassword().getPlainText());
		BatchPoints batchPoints = BatchPoints.database(dbName).retentionPolicy("default").consistency(ConsistencyLevel.ALL).build();
   
		List<ContentLine> contentLines = InfluxContentParser.parse(content);
		
		logger.println("Publishing " + contentLines.size() + " to InfluxDB at " + dbUrl);
		
		for (ContentLine cl : contentLines) {
			Point.Builder builder = Point.measurement(cl.getMeasurementName())
				   	.tag(cl.getTags())
				   	.fields(cl.getFields());
			if (cl.getTimestamp() != null) {
			   	builder.time(cl.getTimestamp(), TimeUnit.NANOSECONDS);
			}
			Point point = builder.build();
		 	batchPoints.point(point);
		} 
   
		influx.write(batchPoints);
		
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
			return "InfluxDB Publisher";
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}
		
		
		@SuppressWarnings("deprecation")
		public ListBoxModel doFillUserCredentialsIDItems(@AncestorInPath ItemGroup<?> context) {
            if (!(context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance()).hasPermission(Computer.CONFIGURE)) {
                return new ListBoxModel();
            }
            return new StandardListBoxModel().withAll(
                    CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, context, ACL.SYSTEM));
                            
        }


	}
}
