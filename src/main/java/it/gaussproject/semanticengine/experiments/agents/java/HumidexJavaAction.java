package it.gaussproject.semanticengine.experiments.agents.java;

import java.util.Map;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;

import it.gaussproject.semanticengine.Engine;
import it.gaussproject.semanticengine.actions.Action;
import it.gaussproject.semanticengine.utils.HumidexCalculator;
import it.gaussproject.semanticengine.utils.XSDDateTimeFormatter;

public class HumidexJavaAction implements Action {

	private static final String LSA_NS = "http://www.gauss.it/lsa/";
	public static final String GAUSS_EXPERIMENTS_NS = "http://experiments.gauss.it/lsa/";
	private static final String SPARQL_PREFIXES = 
		"prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" + 
		"prefix owl: <http://www.w3.org/2002/07/owl#>\n" + 
		"prefix xsd: <http://www.w3.org/2001/XMLSchema#>\n"+
		"prefix sosa: <http://www.w3.org/ns/sosa/>\n" +
		"prefix lsa: <"+LSA_NS+">\n"+
		"prefix lsaex: <"+GAUSS_EXPERIMENTS_NS+">\n";

	@Override
	public void execute(Engine engine, Map<String, String> actionParams, Map<String, Object> paramMap) {
		try {
			String sparqlQuery =
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
			ResultSet results = engine.runSelectTransaction(sparqlQuery);
			double humidex = -1;
			if(results.hasNext()) {
				QuerySolution solution = results.next();
				double temperature = solution.getLiteral("temperature").getDouble();
				double humidity = solution.getLiteral("humidity").getDouble();
				humidex = HumidexCalculator.calculate(temperature, humidity);
			}
			engine.addObservation("http://experiments.gauss.it/lsa/livingHumidexLogicalSensor", XSDDateTimeFormatter.format(), "http://experiments.gauss.it/lsa/livingHumidex", "http://experiments.gauss.it/lsa/living", humidex+"", null, null);
		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
	}
}
