package rocks.inspectit.releaseplugin.influxdb;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.apache.commons.lang.time.DateUtils;

public class InfluxContentParser {

	/**
	 * Literals representing "True" when using the InfluxDB line format.
	 */
	private static final Set<String> TRUE_BOOL_LITERALS = new HashSet<>(
			Arrays.asList("t", "T", "true", "True", "TRUE"));

	/**
	 * Literals representing "False" when using the InfluxDB line format.
	 */
	private static final Set<String> FALSE_BOOL_LITERALS = new HashSet<>(
			Arrays.asList("f", "F", "false", "False", "FALSE"));

	/**
	 * POJO representing the contents of a parsed line.
	 * 
	 * @author Jonas Kunz
	 */
	public static class ContentLine {

		/**
		 * The measurement name.
		 */
		private String measurementName;
		/**
		 * The tag map.
		 */
		private Map<String, String> tags;
		/**
		 * The fields map.
		 */
		private Map<String, Object> fields;

		/**
		 * The timestamp in nanoseconds since the epoche, may be null.
		 */
		private Long timestamp;

		/**
		 * Constructor.
		 */
		public ContentLine() {
			tags = new HashMap<String, String>();
			fields = new HashMap<String, Object>();
		}

		public String getMeasurementName() {
			return measurementName;
		}

		public Map<String, String> getTags() {
			return tags;
		}

		public Map<String, Object> getFields() {
			return fields;
		}

		public Long getTimestamp() {
			return timestamp;
		}

	}

	/**
	 * Parses a given String in InfluxDB line format.
	 * 
	 * @param source
	 *            the source in InfluxDB line format.
	 * @return the parsed contents.
	 */
	public static List<ContentLine> parse(String source) {

		List<ContentLine> results = new ArrayList<InfluxContentParser.ContentLine>();

		String[] lines = source.split("\n");
		for (String line : lines) {
			line = removeWhitespaces(line);
			if (line.isEmpty()) {
				continue;
			}

			ContentLine content = new ContentLine();
			results.add(content);

			String[] spaceSepSegments = splitConsideringQuotes(line);
			int index = 0;
			for (String segment : spaceSepSegments) {
				if (segment.isEmpty()) {
					continue;
				}
				switch (index) {
				case 0:
					parseKey(segment, content);
					break;
				case 1:
					parseFields(segment, content);
					break;
				case 2:
					parseTimestamp(segment, content);
					break;
				default:
					throw new RuntimeException("invalid line! : " + line);
				}
				index++;
			}
		}
		return results;
	}

	/**
	 * Splits the given line with spaces as separator, but takes quotes into
	 * account.
	 * 
	 * @param line
	 *            the line to split
	 * @return the individual splits.
	 */
	private static String[] splitConsideringQuotes(String line) {

		List<String> results = new ArrayList<String>();

		List<Integer> spacePositions = new ArrayList<Integer>();
		boolean wasPrevBackslash = false;
		int quoteCount = 0;
		for (int i = 0; i < line.length(); i++) {
			if (line.charAt(i) == ' ' && !wasPrevBackslash
					&& (quoteCount % 2 == 0)) {
				spacePositions.add(i);
			}
			if (line.charAt(i) == '\"' && !wasPrevBackslash) {
				quoteCount++;
			}
			wasPrevBackslash = line.charAt(i) == '\\';
		}
		for (int i = -1; i < spacePositions.size(); i++) {
			int beg;
			if (i == -1) {
				beg = 0;
			} else {
				beg = spacePositions.get(i) + 1;
			}
			int end;
			if (i == spacePositions.size() - 1) {
				end = line.length();
			} else {
				end = spacePositions.get(i + 1);
			}
			if (beg != end) {
				results.add(line.substring(beg, end));
			}
		}
		return results.toArray(new String[0]);

	}

	/**
	 * parses a timestamp, which is either a nanoseconds value or a value in the format "YYYY-MM-DD-HH-MM-SS".
	 * @param segment the stirng to parse
	 * @param content the result structure
	 */
	private static void parseTimestamp(String segment, ContentLine content) {
		if (segment.contains("-")) {
			Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));

			String[] data = segment.split("-");
			cal.set(Integer.parseInt(data[0]), Integer.parseInt(data[1]) - 1,
					Integer.parseInt(data[2]));
			DateUtils.truncate(cal, Calendar.DATE);
			if (data.length > 3) {
				cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(data[3]));
			}
			if (data.length > 4) {
				cal.set(Calendar.MINUTE, Integer.parseInt(data[4]));
			}
			if (data.length > 5) {
				cal.set(Calendar.SECOND, Integer.parseInt(data[5]));
			}
			content.timestamp = cal.getTimeInMillis() * 1000 * 1000;
		} else {
			content.timestamp = Long.parseLong(segment);
		}
	}

	/**
	 * Parses the fields into the given result structure.
	 * @param segment the string to parse
	 * @param content the result container
	 */
	private static void parseFields(String segment, ContentLine content) {
		String[] commaSegments = segment.split("(?<!\\\\),");
		for (String keyValue : commaSegments) {
			String[] keyValueSplitted = keyValue.split("=");
			if (keyValueSplitted.length != 2) {
				throw new RuntimeException("invalid key value pair " + keyValue);
			}
			content.fields.put(keyValueSplitted[0],
					parseFieldValue(keyValueSplitted[1]));
		}

	}

	/**
	 * Parses a given String into the correct datatype.
	 * @param val
	 * @return the parsed value (Boolean, Integer, Double)
	 */
	private static Object parseFieldValue(String val) {
		if (val.startsWith("\"") && val.endsWith("\"")) {
			return unescape(val.substring(1, val.length() - 1));
		} else if (TRUE_BOOL_LITERALS.contains(val)) {
			return (Boolean) true;
		} else if (FALSE_BOOL_LITERALS.contains(val)) {
			return (Boolean) false;
		} else if (val.endsWith("i")) {
			return Integer.parseInt(val.substring(0, val.length() - 1));
		} else {
			return Double.parseDouble(val);
		}
	}

	/**
	 * Parses the key
	 * @param segment
	 * @param content
	 */
	private static void parseKey(String segment, ContentLine content) {
		String[] commaSegments = segment.split("(?<!\\\\),");
		if (commaSegments.length < 2) {
			throw new RuntimeException("Tags are missing!");
		}
		content.measurementName = commaSegments[0];
		for (int i = 1; i < commaSegments.length; i++) {
			String[] keyValuePair = commaSegments[i].split("(?<!\\\\)=");
			if (keyValuePair.length != 2) {
				throw new RuntimeException("invalid key value pair "
						+ commaSegments[i]);
			}
			content.tags.put(unescape(keyValuePair[0]),
					unescape(keyValuePair[1]));
		}

	}

	private static String unescape(String str) {
		return str.replaceAll(Pattern.quote("\\ "), " ")
				.replaceAll(Pattern.quote("\\,"), ",")
				.replaceAll(Pattern.quote("\\="), "=")
				.replaceAll(Pattern.quote("\\\""), "\"");
	}

	private static String removeWhitespaces(String src) {
		int leadingCount = 0;
		while (Character.isWhitespace(src.charAt(leadingCount))) {
			leadingCount++;
		}

		int traillingCount = 0;
		while (Character.isWhitespace(src.charAt(src.length() - traillingCount
				- 1))) {
			traillingCount++;
		}
		return src.substring(leadingCount, src.length() - traillingCount);
	}

}
