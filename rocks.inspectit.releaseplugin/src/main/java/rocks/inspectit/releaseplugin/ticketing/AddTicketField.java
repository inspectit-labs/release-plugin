package rocks.inspectit.releaseplugin.ticketing;

import java.io.PrintStream;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.text.StrSubstitutor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import rocks.inspectit.releaseplugin.FieldMetadata;
import rocks.inspectit.releaseplugin.IssueUpdateBuilder;
import rocks.inspectit.releaseplugin.JIRAAccessTool;
import rocks.inspectit.releaseplugin.JIRAMetadataCache;
import rocks.inspectit.releaseplugin.JIRAAccessTool.BuildingLambda;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.ComboBoxModel;

public class AddTicketField  extends AbstractDescribableImpl<AddTicketField> {
	private String fieldHumanReadableName;

	
	private String valueToSet;
	
	/**
	 * Constructor.
	 * @param versionToAdd the version to add
	 */
	@DataBoundConstructor
	public AddTicketField(String fieldHumanReadableName, String valueToSet) {
		super();
		this.fieldHumanReadableName = fieldHumanReadableName;
		this.valueToSet = valueToSet;
	}

	


	public String getFieldHumanReadableName() {
		return fieldHumanReadableName;
	}


	public String getValueToSet() {
		return valueToSet;
	}




	/**
	 * Descriptor implementation.
	 * @author JKU
	 *
	 */
	@Extension public static class DescribtorImpl extends Descriptor<AddTicketField> {

		@Override
		public String getDisplayName() {
			return "Specify Field Value";
		}
		
		 /**
         * Combobox populating method.
         * @param jiraCredentialsID the credentials used for access.
         * @return a ComboBoxModel containing the JIRA values as suggestions
         */
        public ComboBoxModel doFillFieldHumanReadableNameItems(@RelativePath("../..") @QueryParameter String jiraCredentialsID) { 	
        	ComboBoxModel result = new ComboBoxModel();
        	for(FieldMetadata field : JIRAMetadataCache.getSingleton().getFieldMetadata(jiraCredentialsID)) {
        		Set<String> allowedTypes = IssueUpdateBuilder.SUPPORTED_TYPES;
        		if(field.isModifiable() && allowedTypes.contains(field.getElementType())) {
                	result.add(field.getHumanReadableName());        			
        		}        		
        	}
    		return result;
        }
		
	}


	public void apply(IssueUpdateBuilder fieldModifications, JIRAAccessTool jira, final StrSubstitutor varReplacer, PrintStream logger) {
		//find the field definition
		List<FieldMetadata> fields = JIRAMetadataCache.getSingleton().getFieldMetadata(jira.getJenkinsCredentialsId());
		
			String fieldName = varReplacer.replace(fieldHumanReadableName);
		FieldMetadata foundField = null;
		
		for(FieldMetadata field : fields) {
			if(field.getHumanReadableName().equalsIgnoreCase(fieldName)) {
				foundField = field; break;
			}
		}
		
		if(foundField == null) {
			throw new RuntimeException("Field with the name \""+fieldName+"\" does not exist!");
		} else if(!foundField.isModifiable()) {
			throw new RuntimeException("Field with the name \""+fieldName+"\" is not modifiable!");
		}
		
		String val =  varReplacer.replace(valueToSet);
		if(!foundField.isArray()) {
			fieldModifications.setFieldValue(foundField.getInternalName(), foundField.getElementType(), val);
		} else {
			fieldModifications.setArrayField(foundField.getInternalName(), foundField.getElementType(), val);
		}
	}




	public FieldMetadata getFieldMeta(String jenkinsCredentialsId, StrSubstitutor varReplacer) {
		String name = varReplacer.replace(fieldHumanReadableName);
		for(FieldMetadata fm : JIRAMetadataCache.getSingleton().getFieldMetadata(jenkinsCredentialsId)) {
			if(fm.getHumanReadableName().equalsIgnoreCase(name)) {
				return fm;
			}
		}
		return null;
	}
}
