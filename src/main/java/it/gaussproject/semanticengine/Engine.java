package it.gaussproject.semanticengine;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.update.UpdateAction;

import it.gaussproject.semanticengine.jena.TransactionEnqueuingDatasetGraphWrapper;
import it.gaussproject.semanticengine.utils.NamedFormatter;

/**
 * The Engine wraps the model (the KB) and augment it
 * with the reactive activation of logical sensors/actuators.
 * 
 * This class is a singleton (since we assume that only
 * one KB exists).
 * 
 * @author Davide Rossi
 */
public class Engine {
	private static Logger LOG = Logger.getGlobal();

	private Model model;
	private DatasetGraph datasetGraph;
	private ModelQueueProcessor modelQueueProcessor;
	BlockingQueue<Model> modelQueue;
	private boolean persistent = false;
	private static Engine instance = new Engine();
	//TODO improve handling of prefixes
	public static final String lsaNS = "http://www.gauss.it/lsa/";
	public static final String gaussMuseumNS = "http://example.museum.gauss.it/";

	public static final String queryPrefixes = "prefix sosa: <http://www.w3.org/ns/sosa/>\n" +
			"prefix oboe: <http://ecoinformatics.org/oboe/oboe.1.0/oboe-core.owl#>\n" +
			"prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
			"prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
			"prefix xsd: <http://www.w3.org/2001/XMLSchema#>\n"+
			"prefix ssn: <http://www.w3.org/ns/ssn/>\n"+
			"prefix owl: <http://www.w3.org/2002/07/owl#>\n"+
			"prefix lsa: <"+lsaNS+">\n" +
			"prefix gmus: <"+gaussMuseumNS+">\n" +
			"base <"+gaussMuseumNS+">\n\n";
	public static final String ttlPrefixes = "@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n" + 
			"@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n" + 
			"@prefix owl: <http://www.w3.org/2002/07/owl#> .\n"+
			"@prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .\n" + 
			"@prefix sosa: <http://www.w3.org/ns/sosa/> .\n" + 
			"@prefix ssn:  <http://www.w3.org/ns/ssn/> .\n" + 
			"@prefix time: <http://www.w3.org/2006/time#> .\n" + 
			"@prefix qudt-1-1: <http://qudt.org/1.1/schema/qudt#> .\n" + 
			"@prefix qudt-unit-1-1: <http://qudt.org/1.1/vocab/unit#> .\n" + 
			"@prefix cdt: <http://w3id.org/lindt/custom_datatypes#> .\n" + 
			"@prefix lsa: <"+lsaNS+"> .\n" + 
			"@prefix gmus: <"+gaussMuseumNS+"> .\n" + 
			"@base <"+gaussMuseumNS+"> .\n\n";
	private static final String proceduresQueryTemplate = 
			"SELECT ?softProcedure ?sensor ?propName ?propValue " + 
			"WHERE '{'" + 
			"	?softProcedure ssn:hasInput <{0}> ; " + 
			"		ssn:implementedBy ?sensor ; " + 
			"		lsa:hasBehavior ?behavior ; " + 
//			"		rdf:type/rdfs:subClassOf lsa:SoftwareProcedure . " + 
			"		rdf:type lsa:SoftwareProcedure . " + 
			"	?behavior lsa:hasActionSpecification ?actionable . " + 
			"	?actionable a lsa:Actionable ; " + 
			"		?propName ?propValue . " + 
//			"	?sensor rdf:type sosa:System . " + 
			"'}'";
	private static final String ACTION_ADD_TTL = gaussMuseumNS+"addTTL";
	private static final String ACTION_SPARQL_CONSTRUCT = gaussMuseumNS+"sparqlQuery";
	private static final String ACTION_SPARQL_UPDATE = gaussMuseumNS+"sparqlQueryUpdate";
	private static final String ACTION_REST = gaussMuseumNS+"restQuery";
	
	/**
	 * This class is intended to run in a service thread
	 * that processes all KB changes that a transaction
	 * listener puts is a queue in the form of in-memory
	 * models.
	 * It looks in these models to see if they carry
	 * observations of observed properties that are the
	 * input of procedures for existing sensors/actuators.
	 * If this is the case the actions specified as
	 * behavior for that procedure are executed.
	 */
	class ModelQueueProcessor implements Runnable {
		private boolean enabled = false;
		private BlockingQueue<Model> queue;
		private Model deathPill = ModelFactory.createDefaultModel();
		
		public ModelQueueProcessor(BlockingQueue<Model> queue) {
			this.queue = queue;
		}

		public void exit() {
			queue.add(this.deathPill);
		}

		@Override
		public void run() {
			try {
				Model takenModel;
				do {
					takenModel = this.queue.take();
					if(takenModel != this.deathPill && this.enabled) {
						checkLogicalSensors(takenModel);
						checkLogicalActuators(takenModel);
					}
				} while(takenModel != deathPill);
			} catch (InterruptedException e) {
				LOG.info("ChangeQueueProcessor::run interrupted");
			}
		}

		public void stop() {
			this.queue.add(this.deathPill);
		}
		
		public void checkLogicalSensors(Model newModel) {
			//find new observations and their observedProperties
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
					query = MessageFormat.format(Engine.queryPrefixes+Engine.proceduresQueryTemplate, obsProp);
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
							LOG.info("Logical sensor activated, executing action: "+actions.get(Engine.lsaNS+"hasType").toString());
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
		public void checkLogicalActuators( Model newModel) {
			//find new observations and their observedProperties
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
					query = MessageFormat.format(Engine.queryPrefixes+Engine.proceduresQueryTemplate, obsProp);
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
							LOG.info("Actuator activated, executing action: "+actions.get(Engine.lsaNS+"hasType").toString());
							runActions(actions, paramMap);
						}
					}
				}
			} catch (Exception e) {
				LOG.warning(e.toString());
				System.out.println(e.getMessage());
			}
		}
		
		void enable(boolean active) {
			this.enabled = active;
		}
	}

	public static Engine getEngine() {
		return Engine.instance;
	}

	public Model getModel() {
		return model;
	}
	
	public static String getQueryprefixes() {
		return Engine.queryPrefixes;
	}

	public void runAction(Map<String, String> actions) {
		runActions(actions, null);
	}

	public void runActions(Map<String, String> actions, Map<String, Object> paramMap) {
		if(paramMap == null) {
			paramMap = new HashMap<>();
		}
		//TODO improve parameter passing, specifically define a SPARQL query action that just fills the params map
		String actionType = actions.get(Engine.lsaNS+"hasType");
		if(actionType.equals(ACTION_ADD_TTL)) {
			String ttlTemplate = actions.get(Engine.lsaNS+"ttlTemplate");
			//TODO this is a workaround, find the correct way to handle escaped quotes in long strings in turtle
			ttlTemplate = ttlTemplate.replace("\\\"", "\"");
			String paramQuery = actions.get(Engine.lsaNS+"paramQuery");
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
		} else if(actionType.equals(ACTION_SPARQL_UPDATE)) {
			String query = actions.get(Engine.lsaNS+"hasCode");
			query = query.replace("\\\"", "\"");
			query = Engine.queryPrefixes+NamedFormatter.format(query, paramMap);
			LOG.info("SPARQLUPDATE running query: "+query);
			runUpdateTransaction(query);
		} else if(actionType.equals(ACTION_SPARQL_CONSTRUCT)) {
			String query = actions.get(Engine.lsaNS+"hasCode");
			query = query.replace("\\\"", "\"");
			query = Engine.queryPrefixes+NamedFormatter.format(query, paramMap);
			LOG.info("SPARQLCONSTRUCT running query: "+query);
			Model newModel = runConstructTransaction(query);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			RDFDataMgr.write(baos, newModel, Lang.TURTLE);
			LOG.info("Result: "+baos.toString(Charset.defaultCharset()));
			runAddTransaction(newModel);
		} else if(actionType.equals(ACTION_REST)) {
			String method = actions.get(Engine.lsaNS+"hasMethod");
			String url = actions.get(Engine.lsaNS+"hasURL");
			String paramQuery = actions.get(Engine.lsaNS+"paramQuery");
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
			String requestBody = actions.get(Engine.lsaNS+"requestBody");
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
	 * 
	 * @param sparqlQuery the query
	 * @return the result of the query
	 */
	public ResultSet runSelectTransaction(String sparqlQuery) {
		return Txn.calculateRead(this.datasetGraph, () -> {
			try(QueryExecution queryExecution = QueryExecutionFactory.create(Engine.queryPrefixes+sparqlQuery, this.model)) {
				ResultSet results = queryExecution.execSelect();
				results = ResultSetFactory.copyResults(results);
				return results;
			}
		});
	}

	/**
	 * Runs a CONSTRUCT SPARQL query against the KB
	 * 
	 * @param sparqlQuery the query
	 * @return the result of the query
	 */
	public Model runConstructTransaction(String sparqlQuery) {
		return Txn.calculateRead(this.datasetGraph, () -> {
			try(QueryExecution queryExecution = QueryExecutionFactory.create(Engine.queryPrefixes+sparqlQuery, this.model)) {
				return queryExecution.execConstruct();
			}		
		});
	}

	/**
	 * Runs an UPDATE SPARQL query against the KB.
	 * For in-memory models, since we can't relay on a transaction listener, we have to
	 * run the query against a full copy of the model and call the change listener on the 
	 * statements that result from the subtraction of the new model with the original one.
	 * 
	 * @param sparqlQuery the query
	 */
	public void runUpdateTransaction(String sparqlQuery) {
		Txn.executeWrite(this.datasetGraph, () -> {
			UpdateAction.parseExecute(sparqlQuery, this.model);
		});
	}

	/**
	 * Transactionally add a model to the KB
	 * 
	 * @param model the model to add
	 */
	public void runAddTransaction(Model newModel) {
		Txn.executeWrite(this.datasetGraph, () -> {
			this.model.add(newModel);
		});
	}

	/**
	 * Transactionally add a turtle fragment to the KB (uses the default prefixes also if
	 * not present in the TTL).
	 * 
	 * @param ttl a turtle string
	 */
	public void runAddTtlTransaction(String ttl) {
		Model newModel = ModelFactory.createDefaultModel().read(new StringReader(Engine.ttlPrefixes+ttl), null, "TTL");
		runAddTransaction(newModel);
	}
	
	/**
	 * Loads a turtle file replacing all the existing content of the model
	 * 
	 * @param filename
	 * @throws FileNotFoundException
	 */
	public void loadTurtle(String filename) throws FileNotFoundException {
		Model newModel = ModelFactory.createDefaultModel().read(new FileReader(filename), null, "TTL");
		Txn.executeWrite(this.datasetGraph, () -> {
			this.model.removeAll();
		});
		runAddTransaction(newModel);
	}

	public void enableListeners(boolean active) {
		this.modelQueueProcessor.enable(active);
	}

	public boolean isPersistent() {
		return persistent;
	}

	/**
	 * This method is used to "promote" an in-memory engine to
	 * a TDB2-backed one.
	 * If a database already exists at location it is cleared.
	 * If the existing in-memory model is not empty it is
	 * added to the persistent one.
	 * 
	 * @param location
	 */
	public void makePersistent(String location) {
		if(this.persistent) {
			throw new RuntimeException("Engine already persistent");
		}
		Dataset dataset = TDB2Factory.connectDataset(location);
		this.datasetGraph = new TransactionEnqueuingDatasetGraphWrapper(dataset.asDatasetGraph(), this.modelQueue);

		Model newModel = ModelFactory.createModelForGraph(this.datasetGraph.getDefaultGraph());
		Txn.executeWrite(this.datasetGraph, () -> {
			newModel.removeAll();
			newModel.add(this.model);
		});
		this.model = newModel;
		this.persistent = true;
	}

	protected QueryExecution getQueryExecution(String query) {
		return QueryExecutionFactory.create(Engine.queryPrefixes+query, this.model);
	}

	/**
	 * Stops the Engine.
	 * Currently just terminates the service thread listening
	 * to KB changes.
	 */
	public void shutdown() {
		this.modelQueueProcessor.stop();
	}
	
	protected Engine() {
		//by default we create an in-memory dataset
		Dataset dataset = DatasetFactory.createTxnMem();
		this.modelQueue = new LinkedBlockingQueue<>();
		//create the wrapped DatasetGraph that puts the triples added in a transaction in the queue
		this.datasetGraph = new TransactionEnqueuingDatasetGraphWrapper(dataset.asDatasetGraph(), this.modelQueue);
		this.model = ModelFactory.createModelForGraph(this.datasetGraph.getDefaultGraph());
		//run the service thread that takes element from the queue to process them
		this.modelQueueProcessor = new ModelQueueProcessor(this.modelQueue);
		new Thread(this.modelQueueProcessor).start();
	}
}
