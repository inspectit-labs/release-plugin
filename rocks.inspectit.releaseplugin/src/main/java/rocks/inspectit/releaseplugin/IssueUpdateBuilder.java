package rocks.inspectit.releaseplugin;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Utility class for building the data sent to /ISSUE_KEY/ PUT requests to update issues.
 * See the JRIA Rest API documentation for more information
 * 
 * @author Jonas Kunz
 *
 */
public class IssueUpdateBuilder {

	/**
	 * The name of the "versions" property used in JSON requests.
	 */
	private static final String VERSION_FIELD = "versions";

	/**
	 * The name of the "comment" property used in JSON requests.
	 */
	private static final String COMMENT_FIELD = "comment";

	/**
	 * Currently supported types for serialization.
	 */
	public static final Set<String> SUPPORTED_TYPES = new HashSet<>(Arrays.asList("any", "number", "string", "version"));
	
	/**
	 * Stores the currently buffered updates.
	 */
	private JsonObject updateOperations;
	
	/**
	 * Constructor.
	 */
	public IssueUpdateBuilder() {
		updateOperations = new JsonObject();
	}
	
	/**
	 * Returns the data to send with the put - request for updating an issue.
	 * @return the finished request
	 */
	public JsonElement getRequestData() {
		JsonObject data = new JsonObject();
		data.add("update", updateOperations);
		return data;
	}
	
	/**
	 * Returns the update-entry of the given field, creating an empty one if necessary.
	 * @param fieldName the name of the field
	 * @return the update list
	 */
	private JsonArray getFieldUpdates(String fieldName) {
		JsonElement elem = updateOperations.get(fieldName);
		if (elem == null) {
			elem = new JsonArray();
			updateOperations.add(fieldName, elem);
		}
		return elem.getAsJsonArray();
	}
	
	/**
	 * 
	 * Adds the given version to the list of affected versions.
	 *  
	 * @param versionName
	 * 		the name of the version to add.
	 */
	public void addAffectedVersion(String versionName) {
		getFieldUpdates(VERSION_FIELD).add(buildAdd(builNameReference(versionName)));
	}
	

	/**
	 * 
	 * Adds the given comment.
	 *  
	 * @param body
	 * 		the body of the comment to add.
	 */
	public void addComment(String body) {
		getFieldUpdates(COMMENT_FIELD).add(buildAdd(buildComment(body)));
	}
	
	/**
	 * Builds a comment JSON object.
	 * @param body the body of the comment
	 * @return a JSON object representing this comment
	 */
	private JsonElement buildComment(String body) {
		JsonObject comment = new JsonObject();
		comment.addProperty("body", body);
		return comment;
	}

	/**
	 * Generic way of setting single-valued field values.
	 * See {@link #SUPPORTED_TYPES} for the supported field types.
	 * @param fieldName the name of the field
	 * @param fieldType the type of the field
	 * @param value the value to set
	 */
	public void setFieldValue(String fieldName, String fieldType, String value) {
		JsonElement newValue = packageValue(fieldType, value);
		getFieldUpdates(fieldName).add(buildSet(newValue));
	}
	
	/**
	 * Generic way of setting (replacing) array fields.
	 * See {@link #SUPPORTED_TYPES} for the supported element types.
	 * @param fieldName the name of the field
	 * @param fieldType the type of the fields elements
	 * @param values the values to set
	 */
	public void setArrayField(String fieldName, String fieldType, String... values) {
		JsonArray arr = new JsonArray();
		for (String value : values) {
			arr.add(packageValue(fieldType, value));
		}
		getFieldUpdates(fieldName).add(buildSet(arr));
	}

	/**
	 * Utiltiy method for building correctly typed JSON elements.
	 * @param fieldType the tye to get
	 * @param value the value
	 * @return the correct JSON representation for the given value
	 */
	private JsonElement packageValue(String fieldType, String value) {
		JsonElement newValue;
		if (fieldType.equals("version")) {
			newValue = builNameReference(value);
		} else {
			newValue = new JsonPrimitive(value);
		}
		return newValue;
	}
	
	/**
	 * Generic way of adding a value to an array field.
	 * See {@link #SUPPORTED_TYPES} for the supported element types.
	 * @param fieldName the name of the field
	 * @param fieldType the type of the fields elements
	 * @param value the value to add
	 */
	public void addFieldValue(String fieldName, String fieldType, String value) {
		JsonElement newValue = packageValue(fieldType, value);
		getFieldUpdates(fieldName).add(buildAdd(newValue));
	}

	/**
	 * Generic way of removing a value from an array field.
	 * See {@link #SUPPORTED_TYPES} for the supported element types.
	 * @param fieldName the name of the field
	 * @param fieldType the type of the fields elements
	 * @param value the value to add
	 */
	public void removeFieldValue(String fieldName, String fieldType, String value) {
		JsonElement newValue = packageValue(fieldType, value);
		getFieldUpdates(fieldName).add(buildRemove(newValue));
	}
	

	/**
	 * 
	 * Removes the given version from the list of affected versions.
	 *  
	 * @param versionName
	 * 		the name of the version to remove.
	 */
	public void removeAffectedVersion(String versionName) {
		getFieldUpdates(VERSION_FIELD).add(buildRemove(builNameReference(versionName)));
	}
	
	/**
	 * @param elementToAdd the element which should be added
	 * @return a JSON Object representing the add-operation
	 */
	private JsonObject buildAdd(JsonElement elementToAdd) {
		JsonObject add = new JsonObject();
		add.add("add", elementToAdd);
		return add;
	}

	/**
	 * @param elementToRemove the element which should be removed
	 * @return a JSON Object representing the remove-operation
	 */
	private JsonObject buildRemove(JsonElement elementToRemove) {
		JsonObject add = new JsonObject();
		add.add("remove", elementToRemove);
		return add;
	}

	/**
	 * @param elementToSet the value which should be set
	 * @return a JSON Object representing the set-operation
	 */
	private JsonObject buildSet(JsonElement elementToSet) {
		JsonObject add = new JsonObject();
		add.add("set", elementToSet);
		return add;
	}
	
	/**
	 * Builds a simple {name : thenamehere} object which is used to reference named objects (like versions).
	 * 
	 * @param name
	 * 		the name to reference
	 * @return
	 * 		the json object representing a reference to this element
	 */
	private JsonObject builNameReference(String name) {
		JsonObject reference = new JsonObject();
		reference.addProperty("name", name);
		return reference;
	}
	
}
