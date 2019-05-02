package it.gaussproject.semanticengine;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.rdf.listeners.StatementListener;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.update.UpdateAction;

import it.gaussproject.semanticengine.utils.NamedFormatter;

/**
 * The Engine wraps the model (the KB) and augment it
 * with the reactive activation of logical sensors/actuators.
 * Since the change notification mechanisms of Jena
 * is unreliable and the transaction support is shaky 
 * we require that all the access to the KB is performed 
 * using the Engine runXXXTransaction methods. 
 * This ensures transactional integrity and the correct 
 * invocation of the listeners.
 * This class is a singleton (since we assume that only
 * one KB exists).
 */
public class Engine {
	private static Logger LOG = Logger.getGlobal();

	private Model model;
	private static Engine instance = new Engine();
	private static EngineChangeListener changeListener = instance.new EngineChangeListener();
	//TODO improve handling of prefixes
	public static final String gaussNS = "http://gauss.it/";
	public static final String gaussMuseumNS = gaussNS+"museum/";

	public static final String queryPrefixes = "prefix sosa: <http://www.w3.org/ns/sosa/>\n" +
			"prefix oboe: <http://ecoinformatics.org/oboe/oboe.1.0/oboe-core.owl#>\n" +
			"prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
			"prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
			"prefix xsd: <http://www.w3.org/2001/XMLSchema#>\n"+
			"prefix ssn: <http://www.w3.org/ns/ssn/>\n"+
			"prefix gauss: <"+gaussNS+">\n" +
			"base <"+gaussMuseumNS+">\n\n";
	public static final String ttlPrefixes = "@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n" + 
			"@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n" + 
			"@prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .\n" + 
			"@prefix sosa: <http://www.w3.org/ns/sosa/> .\n" + 
			"@prefix ssn:  <http://www.w3.org/ns/ssn/> .\n" + 
			"@prefix time: <http://www.w3.org/2006/time#> .\n" + 
			"@prefix qudt-1-1: <http://qudt.org/1.1/schema/qudt#> .\n" + 
			"@prefix qudt-unit-1-1: <http://qudt.org/1.1/vocab/unit#> .\n" + 
			"@prefix cdt: <http://w3id.org/lindt/custom_datatypes#> .\n" + 
			"@prefix gauss: <"+gaussNS+"> .\n" + 
			"@base <"+gaussMuseumNS+"> .\n\n";

	/**
	 * Inner class that listens to changes in the model.
	 * Its task is to "run" logical sensors/activators when needed
	 */
	private class EngineChangeListener extends StatementListener {
		@Override
		public void addedStatements(List<Statement> statements) {
			super.addedStatements(statements);
			checkLogicalSensors(statements, model);
			checkLogicalActuators(statements, model);
		}

		public void checkLogicalSensors(List<Statement> statements, Model model) {
			//find new observations and their observedProperties
			Model newModel = ModelFactory.createDefaultModel().add(statements);
			String query = Engine.queryPrefixes+
					"SELECT ?obsProp ?observation WHERE { "+
					"?observation rdf:type sosa:Observation . "+
					"?observation sosa:observedProperty ?obsProp . }";
			try (QueryExecution queryExecution = QueryExecutionFactory.create(query, newModel)) {
				ResultSet results = queryExecution.execSelect();
				while(results.hasNext()) {
					QuerySolution solution = results.next();
					String obsProp = solution.get("obsProp") == null ? "" : solution.get("obsProp").toString();
					String observation = solution.get("observation") == null ? "" : solution.get("observation").toString();
					Map<String, Object> paramMap = new HashMap<>();
					paramMap.put("_observation", observation);
					//retrieve the queries for procedures that have the observedPropery as input (if any)
					//TODO restrict to procedures linked to sensors?
					query = MessageFormat.format(Engine.queryPrefixes+
						"SELECT ?procedure ?sensor ?propName ?propValue WHERE '{' "+
						"?procedure ssn:hasInput <{0}> . "+
						"?procedure ssn:implementedBy ?sensor . "+
						"?sensor rdf:type sosa:Sensor . "+
						"?procedure rdf:type sosa:Procedure . "+
						"?procedure ssn:implementedBy ?sensor . "+
						"?procedure gauss:action ?action . "+
						"?action ?propName ?propValue '}'", 
						obsProp);
					try (QueryExecution queryExecution2 = QueryExecutionFactory.create(query, model)) {
						Map<String, String> actions = new HashMap<>();
						ResultSet results2 = queryExecution2.execSelect();
						while(results2.hasNext()) {
							QuerySolution solution2 = results2.next();
							String actionPropName = solution2.get("propName").toString();
							String actionPropValue = solution2.get("propValue").toString();
							actions.put(actionPropName, actionPropValue);
						}
						//TODO put current sensor in a action's special key?
						if(actions.size() > 0) {
							LOG.info("Logical sensor activated, executing action: "+actions.get(Engine.gaussNS+"actionType").toString());
							runActions(actions, paramMap);
						}
					}
				}
			} catch (Exception e) {
				LOG.warning(e.toString());
				LOG.warning(e.getMessage());
			}
		}

		//If a "runnable" actuation (with a gauss:action property) is found
		//perform the related action
		public void checkLogicalActuators(List<Statement> statements, Model model) {
			//find new observations and their observedProperties
			Model newModel = ModelFactory.createDefaultModel().add(statements);
			String query = Engine.queryPrefixes+
					"SELECT ?obsProp ?observation WHERE { "+
					"?observation rdf:type sosa:Observation . "+
					"?observation sosa:observedProperty ?obsProp . }";
			try (QueryExecution queryExecution = QueryExecutionFactory.create(query, newModel)) {
				ResultSet results = queryExecution.execSelect();
				while(results.hasNext()) {
					QuerySolution solution = results.next();
					String obsProp = solution.get("obsProp") == null ? "" : solution.get("obsProp").toString();
					String observation = solution.get("observation") == null ? "" : solution.get("observation").toString();
					Map<String, Object> paramMap = new HashMap<>();
					paramMap.put("_observation", observation);

					//retrieve the actions for procedures that have the observerPropery as input (if any)
					//TODO restrict to procedures linked to actuators?
					query = MessageFormat.format(Engine.queryPrefixes+
							"SELECT ?procedure ?action ?propName ?propValue WHERE '{' "+
							"?procedure ssn:hasInput <{0}> . "+
							"?procedure ssn:implementedBy ?actuator . "+
							"?actuator rdf:type sosa:Actuator . "+
							"?procedure rdf:type sosa:Procedure . "+
							"?procedure gauss:action ?action . "+
							"?action ?propName ?propValue '}'", 
							obsProp);
						try (QueryExecution queryExecution2 = QueryExecutionFactory.create(query, model)) {
							Map<String, String> actions = new HashMap<>();
							ResultSet results2 = queryExecution2.execSelect();
							while(results2.hasNext()) {
								QuerySolution solution2 = results2.next();
								String actionPropName = solution2.get("propName").toString();
								String actionPropValue = solution2.get("propValue").toString();
								actions.put(actionPropName, actionPropValue);
							}
							if(actions.size() > 0) {
								LOG.info("Actuator activated, executing action: "+actions.get(Engine.gaussNS+"actionType").toString());
								runActions(actions, paramMap);
							}
						}
				}
			} catch (Exception e) {
				LOG.warning(e.toString());
				System.out.println(e.getMessage());
			}
		}
	}

	public static Engine getEngine() {
		return Engine.instance;
	}

	public Model getModel() {
		return model;
	}

	public void runAction(Map<String, String> actions) {
		runActions(actions, null);
	}
	
	public void runActions(Map<String, String> actions, Map<String, Object> paramMap) {
		if(paramMap == null) {
			paramMap = new HashMap<>();
		}
		String actionType = actions.get(Engine.gaussNS+"actionType");
		if(actionType.equals("ADDTTL")) {
			String ttlTemplate = actions.get(Engine.gaussNS+"ttlTemplate");
			//TODO this is a workaround, find the correct way to handle escaped quotes in long strings in turtle
			ttlTemplate = ttlTemplate.replace("\\\"", "\"");
			String paramQuery = actions.get(Engine.gaussNS+"paramQuery");
			if(paramQuery != null) {
				paramQuery = NamedFormatter.format(paramQuery.replace("\\\"", "\""), paramMap);
			}
			//if a paramQuery is defined we run the select query and only consider the first row
			//we then fill the paramsMap with names and values from that row
			if(paramQuery != null && paramQuery.trim().length() > 0) {
				ResultSet resultSet = runSelectTransaction(paramQuery);
				if(resultSet.hasNext()) {
					QuerySolution querySolution = resultSet.next();
					Iterator<String > propsIterator = querySolution.varNames();
					while(propsIterator.hasNext()) {
						String propName = propsIterator.next();
						String propValue = querySolution.get(propName).toString();
						paramMap.put(propName, propValue);
					}
				}
			}
			//insert a "now" key in the paramsMap with the current date/time
			//so that it is available in subsequent action steps
			paramMap.put("now", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(new Date()));
			String ttl = NamedFormatter.format(ttlTemplate, paramMap);
			LOG.info("Inserting model: \n"+ttl);
			runAddTtlTransaction(ttl);
		} else if(actionType.equals("SPARQLUPDATE")) {
			String query = actions.get(Engine.gaussNS+"query");
			query = query.replace("\\\"", "\"");
			query = NamedFormatter.format(query, paramMap);
			LOG.info("SPARQLUPDATE running query: "+query);
			runUpdateTransaction(query);
		} else if(actionType.equals("SPARQLCONSTRUCT")) {
			String query = actions.get(Engine.gaussNS+"query");
			query = query.replace("\\\"", "\"");
			query = NamedFormatter.format(query, paramMap);
			LOG.info("SPARQLCONSTRUCT running query: "+query);
			Model newModel = runConstructTransaction(query);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			RDFDataMgr.write(baos, newModel, Lang.TURTLE);
			LOG.info("Result: "+baos.toString(Charset.defaultCharset()));
			runAddTransaction(newModel);
		} else if(actionType.equals("REST")) {
			String method = actions.get(Engine.gaussNS+"method");
			String url = actions.get(Engine.gaussNS+"url");
			String paramQuery = actions.get(Engine.gaussNS+"paramQuery");
			if(paramQuery != null) {
				paramQuery = NamedFormatter.format(paramQuery.replace("\\\"", "\""), paramMap);
			}
			//if a paramQuery is defined we run the select query and only consider the first row
			//we then fill the paramsMap with names and values from that row
			if(paramQuery != null && paramQuery.trim().length() > 0) {
				ResultSet resultSet = runSelectTransaction(paramQuery);
				if(resultSet.hasNext()) {
					QuerySolution querySolution = resultSet.next();
					Iterator<String > propsIterator = querySolution.varNames();
					while(propsIterator.hasNext()) {
						String propName = propsIterator.next();
						String propValue = querySolution.get(propName).toString();
						paramMap.put(propName, propValue);
					}
				}
			}
			paramMap.put("now", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(new Date()));
			String requestBody = actions.get(Engine.gaussNS+"requestBody");
			if(requestBody != null) {
				requestBody = NamedFormatter.format(requestBody.replace("\\\"", "\""), paramMap);
			}
			LOG.info("REST: "+method+"ing to "+url+" with body: "+requestBody);
		} else {
			LOG.warning("Unsupported action type: "+actionType);
		}
	}

	/**
	 * Runs a SELECT SPARQL query against the KB
	 * @param sparqlQuery the query
	 * @return the result of the query
	 */
	public ResultSet runSelectTransaction(String sparqlQuery) {
		synchronized(this.model) {
			try (QueryExecution queryExecution = QueryExecutionFactory.create(Engine.queryPrefixes+sparqlQuery, this.model)) {
				ResultSet results = queryExecution.execSelect();
				results = ResultSetFactory.copyResults(results);
				return results;
			}
		}
	}

	/**
	 * Runs a CONSTRUCT SPARQL query against the KB
	 * @param sparqlQuery the query
	 * @return the result of the query
	 */
	public Model runConstructTransaction(String sparqlQuery) {
		synchronized (this.model) {
			try (QueryExecution queryExecution = QueryExecutionFactory.create(Engine.queryPrefixes+sparqlQuery, this.model)) {
				Model newModel = queryExecution.execConstruct();
				return newModel;
			}
		}
	}

	/**
	 * Runs an UPDATE SPARQL query against the KB
	 * @param sparqlQuery the query
	 */
	public void runUpdateTransaction(String sparqlQuery) {
		List<Statement> diffStatements = new ArrayList<>();
		synchronized (this.model) {
			Model copyModel = ModelFactory.createDefaultModel().add(this.model);
			UpdateAction.parseExecute(sparqlQuery, copyModel);
			Model differenceModel = copyModel.difference(this.model);
			this.model.add(differenceModel);
			StmtIterator iterator = differenceModel.listStatements();
			while(iterator.hasNext()) {
				diffStatements.add(iterator.next());
			}
		}
		if(diffStatements.size() > 0) {
			Engine.changeListener.addedStatements(diffStatements);
		}
	}

	/**
	 * Transactionally add a turtle fragment to the KB
	 * @param ttl a turtle string
	 */
	public void runAddTtlTransaction(String ttl) {
		Model newModel = ModelFactory.createDefaultModel().read(new StringReader(Engine.ttlPrefixes+ttl), null, "TTL");
		runAddTransaction(newModel);
	}
	
	/**
	 * Transactionally add a model to the KB
	 * @param model the model to add
	 */
	public void runAddTransaction(Model model) {
		List<Statement> newStatements = new ArrayList<>();
		synchronized(this.model) {
			this.model.add(model);
			StmtIterator iterator = model.listStatements();
			while(iterator.hasNext()) {
				newStatements.add(iterator.next());
			}
		}
		Engine.changeListener.addedStatements(newStatements);
	}

	/**
	 * Loads a turtle file
	 * 
	 * @param filename
	 * @throws FileNotFoundException
	 */
	public void loadTurtle(String filename) throws FileNotFoundException {
		Model newModel = ModelFactory.createDefaultModel().read(new FileReader(filename), null, "TTL");
		runAddTransaction(newModel);
	}

	protected Engine() {
		//TODO read the initialization turtle from configuration
//		InputStream in = getClass().getResourceAsStream("/museum2.ttl");
		this.model = ModelFactory.createDefaultModel();
//		model.read(in, null, "TTL");
		//TODO commented since we do not use Jena unreliable notifications
		//model.register(Engine.changeListener);
	}
}
