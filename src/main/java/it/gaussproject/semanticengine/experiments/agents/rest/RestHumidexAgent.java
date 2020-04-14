package it.gaussproject.semanticengine.experiments.agents.rest;

import java.io.StringReader;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import it.gaussproject.semanticengine.utils.HumidexCalculator;
import it.gaussproject.semanticengine.utils.XSDDateTimeFormatter;

@Path("restagents/humidex")
public class RestHumidexAgent {
	private static Logger LOG = Logger.getGlobal();
	
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createObservation(String json) {
    	try {
    		LOG.finest("RestHumidexAgent got "+json);
			JsonObject jsonObject = Json.createReader(new StringReader(json)).readObject();
			String temperature = jsonObject.getString("temperature", null);
			//TODO: fix ugly workaround to extract numeric values from xsd literals
			if(temperature != null && temperature.contains("^^")) {
				temperature = temperature.substring(0, temperature.indexOf("^^"));
			}
			String humidity = jsonObject.getString("humidity", null);
			if(humidity != null && humidity.contains("^^")) {
				humidity = humidity.substring(0, humidity.indexOf("^^"));
			}
			double humidex = temperature != null && humidity != null ? HumidexCalculator.calculate(Double.parseDouble(temperature), Double.parseDouble(humidity)) : -1;
			JsonObjectBuilder builder = Json.createObjectBuilder();
			builder.add("madeBySensor", "http://experiments.gauss.it/lsa/livingHumidexLogicalSensor");
			builder.add("resultTime", XSDDateTimeFormatter.format());
			builder.add("observedProperty", "http://experiments.gauss.it/lsa/livingHumidex");
			builder.add("hasFeatureOfInterest", "http://experiments.gauss.it/lsa/living");
			builder.add("hasSimpleResult", humidex+"");
			builder.add("nowait", "true");
			JsonObject jsonObservation = builder.build();
			builder = Json.createObjectBuilder();
			builder.add("observation", jsonObservation);

	    	return Response.ok(builder.build().toString()).build();
    	} catch(Throwable t) {
    		LOG.severe(t.toString());
    		return Response.serverError().build();
    	}
    }

}
