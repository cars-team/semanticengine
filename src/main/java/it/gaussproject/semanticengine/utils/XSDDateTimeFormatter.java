package it.gaussproject.semanticengine.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class XSDDateTimeFormatter {
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

	public static String format() {
		return XSDDateTimeFormatter.format(null);
	}
	
	public static String format(Date date) {
		if(date == null) {
			date = new Date();
		}
		return XSDDateTimeFormatter.dateFormat.format(date);
	}
	
	public static Date parse(String dateTime) throws ParseException {
		return XSDDateTimeFormatter.dateFormat.parse(dateTime);
	}
	
}
