package it.gaussproject.semanticengine;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.StringReader;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.vocabulary.RDF;

import it.gaussproject.semanticengine.actions.Action;
import it.gaussproject.semanticengine.actions.AddTTLAction;
import it.gaussproject.semanticengine.actions.JavaAction;
import it.gaussproject.semanticengine.actions.RestAction;
import it.gaussproject.semanticengine.actions.SparqlConstructAction;
import it.gaussproject.semanticengine.actions.SparqlQueryAction;
import it.gaussproject.semanticengine.actions.SparqlUpdateAction;
import it.gaussproject.semanticengine.jena.TransactionEnqueuingDatasetGraphWrapper;
import it.gaussproject.semanticengine.utils.TurtleLiteralParser;
import it.gaussproject.semanticengine.utils.XSDDateTimeFormatter;

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
	private FusekiServer fusekiServer;
	private String sparqlEndpoint = "";
	private String apiEndpoint = "";
	private ModelQueueProcessor modelQueueProcessor;
	private boolean persistent = false;
	private BlockingQueue<Model> modelQueue;
	private int modelQueueMaxSize = -1;
	private Map<String, Action> actions = new HashMap<>();
	private Thread collectorThread;
	private long obsTTL = -1;
	//initialization parameters; can only be set via a builder
	private int initSparqlPort = 9998;
	private int initCollectorMemoryRatio = -1;
	private int initModelQueueSize = -1;
	private int initModelWorkerThreads = 1;
	private boolean initPersistent = false;
	private String initPersistentFile;
	private boolean initNoGC = false;
	private long initObsTTL = 10000;
	private long initCollectorDelay = 50000L;
	//TODO improve handling of prefixes
	private static final String SPARQL_ENDPOINT_DGNAME = "semanticengine";
	public static final String SOSA_NS = "http://www.w3.org/ns/sosa/";
	public static final String SSN_NS = "hhttp://www.w3.org/ns/ssn/";
	public static final String LSA_NS = "http://www.gauss.it/lsa/";
	public static final String GAUSS_EXPERIMENTS_NS = "http://experiments.gauss.it/lsa/";
	//Timings
	private long firstObservationTime = -1;
	private long observationsCount = 0;
	//Stats logging
	private Map<String, String> logStatMap = new HashMap<>();
	private List<String> outputLogKeyList = new ArrayList<String>();

	public static final String ACTION_TYPE_PROPERTY = LSA_NS+"hasActionType";
	public static final String QUERY_PREFIXES = "prefix sosa: <http://www.w3.org/ns/sosa/>\n" +
			"prefix oboe: <http://ecoinformatics.org/oboe/oboe.1.0/oboe-core.owl#>\n" +
			"prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
			"prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
			"prefix xsd: <http://www.w3.org/2001/XMLSchema#>\n"+
			"prefix sosa: <http://www.w3.org/ns/sosa/>\n" + 
			"prefix ssn: <http://www.w3.org/ns/ssn/>\n"+
			"prefix owl: <http://www.w3.org/2002/07/owl#>\n"+
			"prefix lsa: <"+LSA_NS+">\n" +
			"prefix lsaex: <"+GAUSS_EXPERIMENTS_NS+">\n" +
			"base <"+GAUSS_EXPERIMENTS_NS+">\n\n";
	public static final String TTL_PREXIFES = "@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n" + 
			"@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n" + 
			"@prefix owl: <http://www.w3.org/2002/07/owl#> .\n"+
			"@prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .\n" + 
			"@prefix sosa: <http://www.w3.org/ns/sosa/> .\n" + 
			"@prefix ssn:  <http://www.w3.org/ns/ssn/> .\n" + 
			"@prefix time: <http://www.w3.org/2006/time#> .\n" + 
			"@prefix qudt-1-1: <http://qudt.org/1.1/schema/qudt#> .\n" + 
			"@prefix qudt-unit-1-1: <http://qudt.org/1.1/vocab/unit#> .\n" + 
			"@prefix cdt: <http://w3id.org/lindt/custom_datatypes#> .\n" + 
			"@prefix lsa: <"+LSA_NS+"> .\n" + 
			"@prefix lsaex: <"+GAUSS_EXPERIMENTS_NS+"> .\n" + 
			"@base <"+GAUSS_EXPERIMENTS_NS+"> .\n\n";
	private static final String PROCEDURES_QUERY_TEMPLATE = 
			"SELECT ?softProcedure ?logicalElement ?propName ?propValue ?index WHERE '{'\n" + 
			"	?softProcedure ssn:hasInput <{0}> ; \n" + 
			"		ssn:implementedBy ?logicalElement ; \n" + 
			"		lsa:hasBehavior ?behavior ; \n" + 
			"		a lsa:SoftwareProcedure . \n" + 
			"	?behavior lsa:hasActionSpecification ?actionable . \n" + 
			"	?actionable a lsa:Actionable ; \n" + 
			"       lsa:hasIndex ?index ;\n"+
			"		?propName ?propValue . \n" + 
			"'}' ORDER BY ?index";
	//Query to find new observations and their observedProperties
	private static final String QUERY_OBSERVATIONS_AND_OBSERVERDPROPERTIES =
			"SELECT ?obsProp ?observation ?resultTime ?creationTS ?simpleResult WHERE { \n"+
			"?observation rdf:type sosa:Observation ; \n"+
			"   sosa:resultTime ?resultTime ; \n"+
			"   lsa:creationTS ?creationTS ; \n"+
			"	sosa:hasSimpleResult ?simpleResult ; \n"+
			"   sosa:observedProperty ?obsProp . }";
	private static final String ACTION_ADD_TTL = LSA_NS+"addTTLActionType";
	private static final String ACTION_SPARQL_QUERY = LSA_NS+"sparqlQueryActionType";
	private static final String ACTION_SPARQL_CONSTRUCT = LSA_NS+"sparqlConstructActionType";
	private static final String ACTION_SPARQL_UPDATE = LSA_NS+"sparqlUpdateActionType";
	private static final String ACTION_REST = LSA_NS+"restActionType";
	private static final String ACTION_JAVA = LSA_NS+"javaActionType";
	public static final String ENGINE_RESOURCE_NAME = "Engine";

	public static Builder createBuilder() {
		return Builder.createBuilder();
	}
	
	/**
	 * Builder inner class
	 */
	public static class Builder {
		Engine engine;

		public static Builder createBuilder() {
			return new Builder();
		}
		
		private Builder() {
			this.engine = new Engine();
		}
		
		public Builder initSparqlPort(int sparqlPort) {
			this.engine.setInitSparqlPort(sparqlPort);
			return this;
		}

		public Builder initCollectorMemoryRatio(int ratio) {
			this.engine.setInitCollectorMemoryRatio(ratio);
			return this;
		}
		
		public Builder initCollectorDelay(long delay) {
			this.engine.setInitCollectorDelay(delay);
			return this;
		}

		public Builder initModelQueueSize(int modelQueueSize) {
			this.engine.setInitModelQueueSize(modelQueueSize);
			return this;
		}

		public Builder initModelWorkerThreads(int modelWorkerThreads) {
			this.engine.setInitModelWorkerThreads(modelWorkerThreads);
			return this;
		}

		public Builder initNoGC(boolean noGC) {
			this.engine.setInitNoGC(noGC);
			return this;
		}

		public Builder initObsTTL(long ttl) {
			this.engine.setInitObsTTL(ttl);
			return this;
		}

		public Builder initPersistent(String filename) {
			this.engine.setInitPersistent(true);
			this.engine.setInitPersistentFile(filename);
			return this;
		}
		
		public Engine build() {
			if(this.engine == null) {
				throw new RuntimeException("build already called");
			}
			this.engine.init();
			Engine retval = this.engine;
			this.engine = null;
			return retval;
		}

	}
	
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
		private Map<String, List<Map<String, String>>> actionParamCache = new HashMap<>();
		
		public ModelQueueProcessor(BlockingQueue<Model> queue) {
			this.queue = queue;
		}

		public void shutdown() {
			this.queue.clear();
			this.queue.add(this.deathPill);
		}

		@Override
		public void run() {
			try {
				Model takenModel;
				do {
					takenModel = this.queue.take();
					synchronized(this.queue) {
						this.queue.notifyAll();
					}
					if(takenModel != this.deathPill && this.enabled) {
						checkLogicalElement(takenModel);
					}
				} while(takenModel != deathPill);
				this.queue.add(deathPill);
			} catch (InterruptedException e) {
				LOG.info("ChangeQueueProcessor::run interrupted");
			}
		}

		public void stop() {
			this.queue.clear();
			this.queue.add(this.deathPill);
		}
		
		protected Map<String, Object> createParamMap() {
			Map<String, Object> paramMap = new HashMap<>();
			paramMap.put("_sparqlEndpoint", Engine.this.sparqlEndpoint);
			paramMap.put("_apiEndpoint", Engine.this.apiEndpoint);
			paramMap.put("_now", XSDDateTimeFormatter.format());
			return paramMap;
		}

		//If a "runnable" sensor or actuator (with a gauss:action property) is found
		//perform the related action(s)
		public void checkLogicalElement(Model newModel) {
			//find new observations and their observedProperties
			String query = Engine.QUERY_PREFIXES+Engine.QUERY_OBSERVATIONS_AND_OBSERVERDPROPERTIES;
			try (QueryExecution queryExecution = QueryExecutionFactory.create(query, newModel)) {
				ResultSet results = queryExecution.execSelect();
				while(results.hasNext()) {
					QuerySolution solution = results.next();
					String obsProp = solution.get("obsProp") == null ? "" : solution.get("obsProp").toString();
					String observation = solution.get("observation") == null ? "" : solution.get("observation").toString();
					String resultTime = solution.get("resultTime") == null ? "" : solution.get("resultTime").toString();
					String creationTS = solution.get("creationTS") == null ? "" : solution.get("creationTS").toString();
					String simpleResult = solution.get("simpleResult") == null ? "" : solution.get("simpleResult").toString();
					Map<String, Object> paramMap = createParamMap();
					paramMap.put("_observation", observation);
					paramMap.put("_observation_resultTime", resultTime);
					paramMap.put("_observation_creationTS", creationTS);
					paramMap.put("_observation_simpleResult", simpleResult);
					//look for the actions to be run in the cache
					List<Map<String, String>> actionParamList = this.actionParamCache.get(obsProp);
					if(actionParamList == null) {
						//retrieve the queries for procedures that have the observedPropery as input (if any)
						Map<String, String> actionParams = new HashMap<>();
						actionParamList = new ArrayList<>();
						query = MessageFormat.format(Engine.QUERY_PREFIXES+Engine.PROCEDURES_QUERY_TEMPLATE, obsProp);
						try (QueryExecution queryExecution2 = QueryExecutionFactory.create(query, Engine.this.model)) {
							ResultSet results2 = queryExecution2.execSelect();
							//retrieve all the procedures and execute them
							int index = -1;
							while(results2.hasNext()) {
								QuerySolution solution2 = results2.next();
								int  newIndex = solution2.get("index") == null ? -1 : solution2.get("index").asLiteral().getInt();
								String actionPropName = solution2.get("propName").toString();
								String actionPropValue = solution2.get("propValue").toString();
								if(index < 0) { //first index
									index = newIndex;
								} else {
									if(index != newIndex) {
										LOG.info("Logical sensor activated, executing action: "+actionParams.get(Engine.ACTION_TYPE_PROPERTY).toString());
										//new index: add this action params to the list and continue with new action params
										actionParamList.add(actionParams);
										actionParams = new HashMap<>();
										index = newIndex;
									}
								}
								actionParams.put(actionPropName, actionPropValue);
							}
						}
						if(actionParams.size() > 0) {
							actionParamList.add(actionParams);
						}
						//update the cache
						//TODO: invalidate cache from the transaction listener!
						this.actionParamCache.put(obsProp, actionParamList);
					}
					//run the actions collected in the list
					for(Map<String, String> loopActionParams : actionParamList) {
						LOG.info("Logical sensor activated, executing action: "+loopActionParams.get(Engine.ACTION_TYPE_PROPERTY).toString());
						runAction(loopActionParams, paramMap);
					}
				}
			} catch (Exception e) {
				LOG.warning(e.toString()+" "+e.getMessage());
			}
		}
	
		void enable(boolean active) {
			this.enabled = active;
		}
	}

	/*
	 * These setters are private because they are intended to be called only by builder before calling Engine.init
	 */
	private void setInitSparqlPort(int initSparqlPort) {
		this.initSparqlPort = initSparqlPort;
	}

	private void setInitCollectorMemoryRatio(int initCollectorMemoryRatio) {
		this.initCollectorMemoryRatio = initCollectorMemoryRatio;
	}

	private void setInitCollectorDelay(long initCollectorDelay) {
		this.initCollectorDelay = initCollectorDelay;
	}

	private void setInitModelQueueSize(int initModelQueueSize) {
		this.initModelQueueSize = initModelQueueSize;
	}

	private void setInitPersistent(boolean initPersistent) {
		this.initPersistent = initPersistent;
	}

	private void setInitPersistentFile(String initPersistentFile) {
		this.initPersistentFile = initPersistentFile;
	}

	private void setInitNoGC(boolean initNoGC) {
		this.initNoGC = initNoGC;
	}

	private void setInitObsTTL(long initObsTTL) {
		this.initObsTTL = initObsTTL;
	}
	
	private void setInitModelWorkerThreads(int initModelWorkerThreads) {
		this.initModelWorkerThreads = initModelWorkerThreads;
	}

	public Model getModel() {
		return Engine.this.model;
	}
	
	public static String getQueryprefixes() {
		return Engine.QUERY_PREFIXES;
	}

	public void setApiEndpoint(String apiEndpoint) {
		this.apiEndpoint = apiEndpoint;
	}
	
	public void logStat(String key, String entry) {
		this.logStatMap.put(key, entry);
		if(this.outputLogKeyList.contains(key)) {
			//TODO find a different way to prioritize logs
			LOG.severe(key+" - "+entry);
		}
	}

	public void addOutputLogKey(String key) {
		this.outputLogKeyList.add(key);
	}
	
	public Map<String, String> getLogStatMap() {
		return this.logStatMap;
	}
	
	public void runAction(Map<String, String> actions) {
		runAction(actions, null);
	}

	public void runAction(Map<String, String> actionParams, Map<String, Object> paramMap) {
		if(paramMap == null) {
			paramMap = new HashMap<>();
			paramMap.put("now", XSDDateTimeFormatter.format());
		}
		String actionType = actionParams.get(Engine.ACTION_TYPE_PROPERTY);
		Action action = this.actions.get(actionType);
		if(action == null) {
			LOG.warning("Unrecognized action: "+actionType);
		} else {
			LOG.info("runAction "+actionType+" with actionParams: "+actionParams+ " paramMap: "+paramMap);
			try {
				action.execute(this, actionParams, paramMap);
			} catch(Throwable t) {
				LOG.warning("Action execution raised: "+t);
			}
		}

	}

	/**
	 * Adds the statements representing an observation to the knowledge base
	 * 
	 * @param expiration after that moment that observation can be reclaimed from the GC; if set to null the default TTL for this Engine is used
	 */
	public void addObservation(String madeBySensor, String resultTime, String observedProperty, String hasFeatureOfInterest, String hasSimpleResult, String observationSubtype, Date expiration) {
		LOG.info("Adding observation [madeBySensor: "+madeBySensor+" observedProperty: "+observedProperty+ " hasFeatureOfInterest: "+hasFeatureOfInterest+" hasSimpleResult: "+hasSimpleResult);
		//timings stuff
		if(this.firstObservationTime < 0) {
			this.firstObservationTime = System.currentTimeMillis();
		}
		this.observationsCount++;

		List<Statement> statements = new ArrayList<>();
		String observationURI = observedProperty+"/observations#"+resultTime;
		Resource observationResource = ResourceFactory.createResource(observationURI);
		statements.add(ResourceFactory.createStatement(observationResource, RDF.type, ResourceFactory.createResource(SOSA_NS+"Observation")));
		if(observationSubtype != null) {
			statements.add(ResourceFactory.createStatement(observationResource, RDF.type, ResourceFactory.createResource(observationSubtype)));
		}
		statements.add(ResourceFactory.createStatement(observationResource, ResourceFactory.createProperty(SOSA_NS+"madeBySensor"), ResourceFactory.createResource(madeBySensor)));
		statements.add(ResourceFactory.createStatement(observationResource, ResourceFactory.createProperty(SOSA_NS+"resultTime"), ResourceFactory.createTypedLiteral(resultTime, XSDDatatype.XSDdateTime)));
		statements.add(ResourceFactory.createStatement(observationResource, ResourceFactory.createProperty(LSA_NS+"creationTS"), ResourceFactory.createTypedLiteral(""+System.nanoTime(), XSDDatatype.XSDlong)));
		statements.add(ResourceFactory.createStatement(observationResource, ResourceFactory.createProperty(SOSA_NS+"observedProperty"), ResourceFactory.createResource(observedProperty)));
		if(hasSimpleResult != null) {
			Literal simpleResultLiteral = TurtleLiteralParser.parse(hasSimpleResult);
			statements.add(ResourceFactory.createStatement(observationResource, ResourceFactory.createProperty(SOSA_NS+"hasSimpleResult"), simpleResultLiteral));
		}
		if(expiration != null || this.obsTTL != -1) {
			if(expiration == null) {
				expiration = Date.from(Instant.now().plusMillis(this.obsTTL));
			}
			String expiresString = XSDDateTimeFormatter.format(expiration); 
			statements.add(ResourceFactory.createStatement(observationResource, ResourceFactory.createProperty(LSA_NS+"expires"), ResourceFactory.createTypedLiteral(expiresString, XSDDatatype.XSDdateTime)));
		}

		statements.add(ResourceFactory.createStatement(observationResource, ResourceFactory.createProperty(SOSA_NS+"hasFeatureOfInterest"), ResourceFactory.createResource(hasFeatureOfInterest)));
		
		runAddTransaction(statements);
	}

	/**
	 * Runs a SELECT SPARQL query against the KB
	 * 
	 * @param sparqlQuery the query
	 * @return the result of the query
	 */
	public ResultSet runSelectTransaction(String sparqlQuery) {
		return Txn.calculateRead(this.datasetGraph, () -> {
			try(QueryExecution queryExecution = QueryExecutionFactory.create(Engine.QUERY_PREFIXES+sparqlQuery, this.model)) {
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
			try(QueryExecution queryExecution = QueryExecutionFactory.create(Engine.QUERY_PREFIXES+sparqlQuery, this.model)) {
				return queryExecution.execConstruct();
			}		
		});
	}

	/*
	 * Blocks the caller if the model queue is too large.
	 */
	public void waitQueue() {
		if(this.modelQueueMaxSize != -1) {
			while(this.modelQueue.size() > this.modelQueueMaxSize) {
				synchronized(this.modelQueue) {
					try {
						if(this.modelQueue.size() > this.modelQueueMaxSize) {
							this.modelQueue.wait();
							this.modelQueue.notifyAll();
						}
					} catch(InterruptedException e) {
					}
				}
			}
		}
	}
	
	/**
	 * Runs an UPDATE SPARQL query against the KB.
	 * 
	 * @param sparqlQuery the query
	 */
	public void runUpdateTransaction(String sparqlQuery) {
		Txn.executeWrite(this.datasetGraph, () -> {
			UpdateAction.parseExecute(sparqlQuery, this.model);
		});
	}

	/**
	 * Transactionally add statements to the KB
	 * 
	 * @param model the model to add
	 */
	public void runAddTransaction(List<Statement> statements) {
		Txn.executeWrite(this.datasetGraph, () -> {
			this.model.add(statements);
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
	public void runAddTTLTransaction(String ttl) {
		Model newModel = ModelFactory.createDefaultModel().read(new StringReader(Engine.TTL_PREXIFES+ttl), null, "TTL");
		runAddTransaction(newModel);
	}

	/**
	 * Loads a turtle file in the KB
	 * 
	 * @param filename
	 * @throws FileNotFoundException
	 */
	public void loadTurtle(String filename) throws FileNotFoundException {
		Model newModel = ModelFactory.createDefaultModel().read(new FileReader(filename), null, "TTL");
		runAddTransaction(newModel);
	}

	public void enableListeners(boolean active) {
		this.modelQueueProcessor.enable(active);
	}

	public boolean isPersistent() {
		return this.persistent;
	}

	protected QueryExecution getQueryExecution(String query) {
		return QueryExecutionFactory.create(Engine.QUERY_PREFIXES+query, this.model);
	}

	public long size() {
		return this.model.size();
	}
	
	public int getModelQueueSize() {
		return this.modelQueue.size();
	}
	
	/**
	 * Stops the Engine.
	 */
	public void shutdown() {
		this.modelQueueProcessor.stop();
		this.fusekiServer.stop();
		if(this.collectorThread != null) {
			this.collectorThread.interrupt();
		}
		if(this.observationsCount > 0) {
			long runningTime = System.currentTimeMillis()-this.firstObservationTime;
			LOG.info("Served "+this.observationsCount+" observations in "+runningTime+"ms - obs/s: "+(this.observationsCount/(runningTime/1000)));
		}
	}

	private Engine() {
	}
	
	/**
	 * Creates an Engine.
	 * If initPersistent has been called a TDB2-backed is created, 
	 * otherwise an in-memory is created.
	 * If a database already exists at that location, it is cleared.
	 */
	private void init() {
		//initialize instance variables from static initialized ones
		this.obsTTL = this.initObsTTL;

		//create the datagraph attaching the transaction listeners
		if(this.initPersistent) {
			Dataset dataset = TDB2Factory.connectDataset(this.initPersistentFile);
			this.datasetGraph = new TransactionEnqueuingDatasetGraphWrapper(dataset.asDatasetGraph(), this.modelQueue);
			this.model = ModelFactory.createModelForGraph(this.datasetGraph.getDefaultGraph());
			this.persistent = true;
		} else {
			Dataset dataset = DatasetFactory.createTxnMem();
			this.modelQueue = new LinkedBlockingQueue<>();
			if(this.initModelQueueSize > 0) {
				this.modelQueueMaxSize = this.initModelQueueSize;
			}
			//create the wrapped DatasetGraph that puts the triples added in a transaction in the queue
			this.datasetGraph = new TransactionEnqueuingDatasetGraphWrapper(dataset.asDatasetGraph(), this.modelQueue);
			this.model = ModelFactory.createModelForGraph(this.datasetGraph.getDefaultGraph());
		}

		//run the sparql endpoint
		this.sparqlEndpoint = "http://localhost:"+this.initSparqlPort+"/"+Engine.SPARQL_ENDPOINT_DGNAME;
		LOG.info("Running SPARQL endpoint at "+sparqlEndpoint);
		this.fusekiServer = FusekiServer.create().port(this.initSparqlPort).add(Engine.SPARQL_ENDPOINT_DGNAME, this.datasetGraph).loopback(false).build().start();

		//initialize the action executors
		this.actions.put(ACTION_SPARQL_QUERY, new SparqlQueryAction());
		this.actions.put(ACTION_SPARQL_CONSTRUCT, new SparqlConstructAction());
		this.actions.put(ACTION_SPARQL_UPDATE, new SparqlUpdateAction());
		this.actions.put(ACTION_ADD_TTL, new AddTTLAction());
		this.actions.put(ACTION_REST, new RestAction());
		this.actions.put(ACTION_JAVA, new JavaAction());

		//run the collector
		if(!this.initNoGC) {
			this.collectorThread = new Thread(new Collector(this, this.initCollectorMemoryRatio, this.initCollectorDelay), "Collector");
			this.collectorThread.start();
		}
		
		//run the service threads that takes element from the queue feed by the transaction listeners to process them
		//TODO: use a thread pool
		this.modelQueueProcessor = new ModelQueueProcessor(this.modelQueue);
		for(int i = 0; i < this.initModelWorkerThreads; i++) {
			new Thread(this.modelQueueProcessor, "model-worker-"+i).start();
		}
	}
}
