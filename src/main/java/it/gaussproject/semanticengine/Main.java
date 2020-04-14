package it.gaussproject.semanticengine;

import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import it.gaussproject.semanticengine.api.API;
import it.gaussproject.semanticengine.experiments.agents.rest.RestAgent;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class.
 * This runs the reactive engine and the REST API.
 * It also listens to the standard input for SPARQL queries
 * to be run against the KB (these are limited to SELECT, 
 * INSERT, ADD and CONSTRUCT).
 * Queries can span multiple lines and are terminated by
 * a line containing a single dot character.
 */
public class Main {
	private static Logger LOG = Logger.getGlobal();
    // Base URI the Grizzly HTTP server will listen on
    public static final String BASE_URI_FORMAT = "http://0.0.0.0:%d/engineapi/";
    public static final int DEFAULT_PORT = 9876;
    public static final int DEFAULT_SPARQL_PORT = 9998;
    private enum QueryModes { SELECT, UPDATE, CONSTRUCT };

    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     * @return Grizzly HTTP server.
     * @throws IOException 
     */
    public static HttpServer startServer(int port, String packageName, Engine engine) throws IOException {
        // create a resource config that scans for JAX-RS resources and providers
        // in package packageName
        final ResourceConfig rc = new ResourceConfig().packages(packageName);
        if(engine != null) {
        	rc.property(Engine.ENGINE_RESOURCE_NAME, engine);
        }

        // create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI
        HttpServer httpServer = GrizzlyHttpServerFactory.createHttpServer(URI.create(String.format(BASE_URI_FORMAT, port)), rc);
        httpServer.start();
        return httpServer;
    }

    /**
     * Main loop: starts the HTTP servers and waits for console queries.
     * @param port the HTTP server port
     * @throws IOException 
     */
    public static void run(int port, Engine engine) throws IOException {
        final HttpServer apiServer = startServer(port, API.class.getPackage().getName(), engine);
        final HttpServer restagentsServer = startServer(port+1, RestAgent.class.getPackage().getName(), null);
        System.out.println(String.format(
        		"Jersey EngineAPI started with WADL available at %sapplication.wadl\n"+
                "Jersey RestAgentAPI started with WADL available at %sapplication.wadl\n"+
        		"You can enter multiline SPARQL queries here; end your query with a line only containing \".\"\n"+
                "The following prefixes can be used in your queries:\n"+Engine.getQueryprefixes()+
                "Enter an empty query (\".\") to exit\n"+
                "Enter \"-\" to display stat info\n", String.format(BASE_URI_FORMAT, port), String.format(BASE_URI_FORMAT, port+1)));
        LOG.info("Running from "+System.getProperty("user.dir"));
        try(Scanner scanner = new Scanner(System.in)) {
	        String query = "";
	        boolean done = false;
	        QueryModes queryMode = QueryModes.SELECT;

	        do {
	        	String line = scanner.nextLine();
	        	if(line.equals("-") && query.equals("")) { //a single "-" with no multi-line query pending
	        		System.out.println("--- Collected stats ---");
	        		for(String key : engine.getLogStatMap().keySet()) {
	        			System.out.println(key+" - "+engine.getLogStatMap().get(key));
	        		}
	        		System.out.println("Activation queue size: "+engine.getModelQueueSize());
	        	} else if(line.equals(".")) {
	        		if(query.equals("")) {
	        			done = true;
	        		} else {
	    	        	try {
	    	        		if(queryMode == QueryModes.SELECT) {
	    	        			ResultSet results = engine.runSelectTransaction(Engine.QUERY_PREFIXES+query);
		    		        	System.out.println(ResultSetFormatter.asText(results));
	    	        		} else if(queryMode == QueryModes.UPDATE) {
	    	        			engine.runUpdateTransaction(Engine.QUERY_PREFIXES+query);
	    	        		} else if(queryMode == QueryModes.CONSTRUCT) {
	    	        			Model model = engine.runConstructTransaction(Engine.QUERY_PREFIXES+query);
	    	        	    	RDFDataMgr.write(System.out, model, Lang.TURTLE);
	    	        		}
	    	        	} catch(Exception e) {
	    	        		System.out.println(e);
	    	        	} finally {
							query = "";
						}
	        		}
	        	} else {
	        		if(query.equals("")) { //first line of a new query
	        			if(line.toUpperCase().startsWith("SELECT ")) {
	        				queryMode = QueryModes.SELECT;
	        			} else if(line.toUpperCase().startsWith("INSERT ") || line.toUpperCase().startsWith("ADD ") || line.toUpperCase().startsWith("DELETE ")) {
	        				queryMode = QueryModes.UPDATE;
	        			} else if(line.toUpperCase().startsWith("CONSTRUCT ")) {
	        				queryMode = QueryModes.CONSTRUCT;
	        			}
	        		}
	        		query = query+"\n"+line;
	        	}
	        } while(!done);
        }
        engine.shutdown();
        apiServer.shutdownNow();
        restagentsServer.shutdown();
    }

    private static void usage() {
    	System.out.println("Usage: semanticengine [-d database_file] [-tb tbox_turtle_file] [-ab abox_turtle_file] [-mr collector_memory_ratio] [-t logic_execution_threads] "+
    		"[-q logical_activation_queue_size] [-p HTTP_port] [-ng] [-gt GC_delay] [-ot default_observation_ttl_ms] [-v] [-log comma_separated_logkeys]");
    }

    /**
     * Main method.
     */
    public static void main(String[] args) throws IOException {
    	LOG.setLevel(Level.WARNING);

    	int index = 0;
		String aBoxTTLlFile = null, tBoxTTLlFile = null;
		int port = DEFAULT_PORT;
		boolean usage = false;
		Engine.Builder engineBuilder = Engine.createBuilder();
		List<String> logKeys = new ArrayList<>();
		while(index < args.length && args[index].startsWith("-")) {
			if(args[index].equals("-ab")) {
				index++;
				aBoxTTLlFile = args[index];
			} else if(args[index].equals("-tb")) {
				index++;
				tBoxTTLlFile = args[index];
			} else if(args[index].equals("-mr")) {
				index++;
				engineBuilder.initCollectorMemoryRatio(Integer.parseInt(args[index]));
			} else if(args[index].equals("-gt")) {
				index++;
				engineBuilder.initCollectorDelay(Long.parseLong(args[index]));
			} else if(args[index].equals("-p")) {
				index++;
				port = Integer.parseInt(args[index]);
			} else if(args[index].equals("-s")) {
				index++;
				engineBuilder.initSparqlPort(Integer.parseInt(args[index]));
			} else if(args[index].equals("-q")) {
				index++;
				engineBuilder.initModelQueueSize(Integer.parseInt(args[index]));
			} else if(args[index].equals("-t")) {
				index++;
				engineBuilder.initModelWorkerThreads(Integer.parseInt(args[index]));
			} else if(args[index].equals("-d")) {
				index++;
				engineBuilder.initPersistent(args[index]);
			} else if(args[index].equals("-ng")) {
				engineBuilder.initNoGC(true);
			} else if(args[index].equals("-ot")) {
				index++;
				engineBuilder.initObsTTL(Integer.parseInt(args[index]));
			} else if(args[index].equals("-log")) {
				index++;
				logKeys = Arrays.asList(args[index].split(","));
			} else if(args[index].equals("-v")) {
		    	LOG.setLevel(Level.ALL);
			} else {
				usage = true;
			}
			index++;
		}
		if(usage) {
			usage();
		} else {
			Engine engine = engineBuilder.build();
			if(aBoxTTLlFile != null) {
				LOG.info("Loading "+aBoxTTLlFile);
				engine.loadTurtle(aBoxTTLlFile);
			}
			if(tBoxTTLlFile != null) {
				LOG.info("Loading "+tBoxTTLlFile);
				engine.loadTurtle(tBoxTTLlFile);
			}
			for(String logKey : logKeys) {
				engine.addOutputLogKey(logKey);
			}
			engine.enableListeners(true);
	    	run(port, engine);
		}
    }
}
