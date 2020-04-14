package it.gaussproject.semanticengine.api;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.logging.Logger;

import javax.json.JsonObject;

import it.gaussproject.semanticengine.Engine;
import it.gaussproject.semanticengine.utils.XSDDateTimeFormatter;

public class ApiUtils {
	private static Logger LOG = Logger.getGlobal();

	public static String addJsonObservation(Engine engine, JsonObject jsonObservation) {
		String madeBySensor = jsonObservation.getString("madeBySensor");
		String resultTime = jsonObservation.getString("resultTime", null);
		if(resultTime == null) {
			resultTime = XSDDateTimeFormatter.format();
		}
		String observedProperty = jsonObservation.getString("observedProperty");
		String hasFeatureOfInterest = jsonObservation.getString("hasFeatureOfInterest");
		String hasSimpleResult = jsonObservation.getString("hasSimpleResult", null);
		String observationSubtype = jsonObservation.getString("observationSubtype", null);
		String expirationDateTime = jsonObservation.getString("expiration", null);
		Date expiration = null;
		if(expirationDateTime != null) {
			try {
				expiration = XSDDateTimeFormatter.parse(expirationDateTime);
			} catch (ParseException e) {
				LOG.info("Invalid expiration dateTime: "+expirationDateTime);
			}
		} else {
			String expirationSeconds = jsonObservation.getString("expirationSeconds", null);
			if(expirationSeconds != null) {
				expiration = Date.from(Instant.now().plusSeconds(Integer.parseInt(expirationSeconds)));
			}
		}
		engine.addObservation(madeBySensor, resultTime, observedProperty, hasFeatureOfInterest, hasSimpleResult, observationSubtype, expiration);
		return resultTime;
	}
}
