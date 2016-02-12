package rocks.inspectit.releaseplugin.ticketing;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Descriptor;
import hudson.util.ComboBoxModel;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
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


/**
 * 
 * Modification selectable for tickets to add a version to the list of affected version.
 * 
 * @author Jonas Kunz
 *
 */
public class ModifyArrayFieldModification extends TicketModification {

	private String fieldHumanReadableName;


	private String modificationType;
	
	private String modificationValue;
	
	/**
	 * Constructor.
	 * @param versionToAdd the version to add
	 */
	@DataBoundConstructor
	public ModifyArrayFieldModification(String fieldHumanReadableName,String modificationType, String modificationValue) {
		super();
		this.fieldHumanReadableName = fieldHumanReadableName;
		this.modificationValue = modificationValue;
		this.modificationType = modificationType == null || modificationType.isEmpty() ? "add" : modificationType;
	}

	


	public String getFieldHumanReadableName() {
		return fieldHumanReadableName;
	}


	public String getModificationType() {
		return modificationType;
	}




	public String getModificationValue() {
		return modificationValue;
	}






	/**
	 * Descriptor implementation.
	 * @author JKU
	 *
	 */
	@Extension public static class DescribtorImpl extends Descriptor<TicketModification> {

		@Override
		public String getDisplayName() {
			return "Add / Remove Field Value";
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
        		
        		if(field.isArray() && field.isModifiable() && allowedTypes.contains(field.getElementType())) {
                	result.add(field.getHumanReadableName());        			
        		}        		
        	}
    		
    		return result;
        }
		
	}


	@Override
	public void apply(String ticketKey, JIRAAccessTool jira, final StrSubstitutor varReplacer, PrintStream logger) {
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
		} else if(!foundField.isArray()) {
			throw new RuntimeException("Field with the name \""+fieldName+"\" is not an array field, use the set-modifications!");
		}
		

		final FieldMetadata foundFieldFinal = foundField;
		jira.updateTicket(ticketKey, new BuildingLambda<IssueUpdateBuilder>() {
			@Override
			public void build(IssueUpdateBuilder b) {
				if(modificationType.equals("add")) {
					b.addFieldValue(foundFieldFinal.getInternalName(), foundFieldFinal.getElementType(), varReplacer.replace(modificationValue));					
				} else if(modificationType.equals("remove")){
					b.removeFieldValue(foundFieldFinal.getInternalName(), foundFieldFinal.getElementType(), varReplacer.replace(modificationValue));						
				} else if(modificationType.equals("replace")){
					b.setArrayField(foundFieldFinal.getInternalName(), foundFieldFinal.getElementType(), varReplacer.replace(modificationValue));						
				}
			}
		});
	}
}
