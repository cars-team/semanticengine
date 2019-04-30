package it.gaussproject.semanticengine.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * After https://codereview.stackexchange.com/questions/194446/messageformat-format-with-named-parameters
 */
public class NamedFormatter {
	private static final Pattern RE = Pattern.compile(
	        "(\\\\.)" +         // Treat any character after a backslash literally 
	        "|" +
	        "(\\$\\{([^\\}]+)\\})"  // Look for ${keys} to replace
	    );
	
	private NamedFormatter() {}
	
	public static String format(String fmt, Map<String, Object> values) {
		String format = fmt;
		Matcher matcher = RE.matcher(fmt);
		while(matcher.find()) {
			if(matcher.group(1) == null) { //it is not just an escape
				String match = matcher.group(2);
				String key = matcher.group(3);
				String value = values.get(key) == null ? "UNDEFINED" : values.get(key).toString();
				format = format.replace(match, value);
			}
		}
		return format;
    }
	
	public static void main(String args[]) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("hello", "HELLO");
		parameters.put("world", "WORLD");
		System.out.println(NamedFormatter.format("Hello ${world} - ${hello} world \\${hello_world}", parameters));
	}
}
