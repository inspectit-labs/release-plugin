package rocks.inspectit.releaseplugin;

import com.atlassian.jira.rest.client.api.domain.Field;
import com.atlassian.jira.rest.client.api.domain.FieldSchema;

/**
 * Class used for storing the meta information about a JIRA field.
 * @author Jonas Kunz
 */
public class FieldMetadata {
	
	/**
	 * The internal name (id) of the field.
	 */
	private String internalName;
	
	/**
	 * The human-readable, meaningful name of the field.
	 */
	private String humanReadableName;
	
	/**
	 * Flag indicating that the given field is an array-field.
	 */
	private boolean isArray;
	/**
	 * Flag indicating whether the field is modifiable.
	 */
	private boolean isModifiable;
	
	/**
	 * The type of the field (or of the elements if it is an array).
	 */
	private String elementType;
	
	/**
	 * Constructor.
	 * @param f the field to use for extracting the metadata.
	 */
	public FieldMetadata(Field f) {
		internalName = f.getId();
		humanReadableName = f.getName();
		
		FieldSchema schema = f.getSchema();
		if (schema != null) {
			isModifiable = true;
			if (schema.getType().equalsIgnoreCase("array")) {
				isArray = true;
				elementType = schema.getItems();
			} else {
				isArray = false;
				elementType = schema.getType();
			}
			
		} else {
			isModifiable = false;
		}
	}

	public String getInternalName() {
		return internalName;
	}

	public String getHumanReadableName() {
		return humanReadableName;
	}

	public boolean isArray() {
		return isArray;
	}

	public String getElementType() {
		return elementType;
	}

	public boolean isModifiable() {
		return isModifiable;
	}
	
	
	
	

}
