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

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;

import it.gaussproject.semanticengine.utils.HumidexCalculator;
import it.gaussproject.semanticengine.utils.XSDDateTimeFormatter;

@Path("restagents/humidexfull")
public class RestHumidexFullAgent {
	private static Logger LOG = Logger.getGlobal();
	
	private static final String LSA_NS = "http://www.gauss.it/lsa/";
	public static final String GAUSS_EXPERIMENTS_NS = "http://experiments.gauss.it/lsa/";
	private static final String SPARQL_PREFIXES = 
		"prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" + 
		"prefix owl: <http://www.w3.org/2002/07/owl#>\n" + 
		"prefix xsd: <http://www.w3.org/2001/XMLSchema#>\n"+
		"prefix sosa: <http://www.w3.org/ns/sosa/>\n" +
		"prefix lsa: <"+LSA_NS+">\n"+
		"prefix lsaex: <"+GAUSS_EXPERIMENTS_NS+">\n";

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createObservation(String json) {
    	try {
    		LOG.finest("RestHumidexFUllAgent got "+json);
			JsonObject jsonObject = Json.createReader(new StringReader(json)).readObject();
			String sparqlEndpoint = jsonObject.getString("sparqlEndpoint", "http://localhost:9998/semanticengine");
//			String apiEndpoint = jsonObject.getString("apiEndpoint", "http://localhost:9876/engineapi/");
			String query =
				SPARQL_PREFIXES +
				"SELECT ?temperature ?humidity WHERE {\n" + 
				"    {\n" + 
				"        SELECT ?temperature WHERE {\n" + 
				"            ?tobs a sosa:Observation;\n" + 
				"                sosa:observedProperty lsaex:livingTemperature;\n" + 
				"                sosa:hasSimpleResult ?temperature;\n" + 
				"                sosa:resultTime ?resultTime\n" + 
				"        } ORDER BY DESC (?resultTime) LIMIT 1\n" + 
				"    }\n" + 
				"    {\n" + 
				"        SELECT ?humidity WHERE {\n" + 
				"            ?hobs a sosa:Observation;\n" + 
				"                sosa:observedProperty lsaex:livingHumidity;\n" + 
				"                sosa:hasSimpleResult ?humidity;\n" + 
				"                sosa:resultTime ?resultTime\n" + 
				"        } ORDER BY DESC (?resultTime) LIMIT 1\n" + 
				"    }\n" + 
				"}";
			if(sparqlEndpoint != null) {
				QueryExecution queryExecution = QueryExecutionFactory.sparqlService(sparqlEndpoint, query);
				ResultSet results = queryExecution.execSelect();
				double humidex = -1;
				if(results.hasNext()) {
					QuerySolution solution = results.next();
					double temperature = solution.getLiteral("temperature").getDouble();
					double humidity = solution.getLiteral("humidity").getDouble();
					humidex = HumidexCalculator.calculate(temperature, humidity);
				}
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
			} else {
				return Response.serverError().build();
			}
    	} catch(Throwable t) {
    		LOG.severe(t.toString());
    		return Response.serverError().build();
    	}
    }

}
