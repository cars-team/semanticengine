package it.gaussproject.semanticengine;

import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.net.URI;
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
    public static final String BASE_URI = "http://localhost:9876/engineapi/";
    private enum QueryModes { SELECT, UPDATE, CONSTRUCT };

    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     * @return Grizzly HTTP server.
     */
    public static HttpServer startServer() {
        // create a resource config that scans for JAX-RS resources and providers
        // in it.gaussproject.semanticengine package
        final ResourceConfig rc = new ResourceConfig().packages("it.gaussproject.semanticengine");

        // create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI
        return GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc);
    }

    /**
     * Main method.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
    	LOG.setLevel(Level.ALL);
        final HttpServer server = startServer();
        System.out.println(String.format("Jersey app started with WADL available at "
                + "%sapplication.wadl\n"+
        		"You can enter multiline SPARQL queries here; last line must only contain \".\""+
                "Enter an empty query to exit", BASE_URI));
        LOG.info("Running from "+System.getProperty("user.dir"));
        try(Scanner scanner = new Scanner(System.in)) {
	        String query = "";
	        boolean done = false;
	        QueryModes queryMode = QueryModes.SELECT;

	        do {
	        	String line = scanner.nextLine();
	        	if(line.equals(".")) {
	        		if(query.equals("")) {
	        			done = true;
	        		} else {
	    	        	try {
	    	        		if(queryMode == QueryModes.SELECT) {
	    	        			ResultSet results = Engine.getEngine().runSelectTransaction(Engine.queryPrefixes+query);
		    		        	System.out.println(ResultSetFormatter.asText(results));
	    	        		} else if(queryMode == QueryModes.UPDATE) {
	    	        			Engine.getEngine().runUpdateTransaction(Engine.queryPrefixes+query);
	    	        		} else if(queryMode == QueryModes.CONSTRUCT) {
	    	        			Model model = Engine.getEngine().runConstructTransaction(Engine.queryPrefixes+query);
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
	        			} else if(line.toUpperCase().startsWith("INSERT ") || line.toUpperCase().startsWith("ADD ")) {
	        				queryMode = QueryModes.UPDATE;
	        			} else if(line.toUpperCase().startsWith("CONSTRUCT ")) {
	        				queryMode = QueryModes.CONSTRUCT;
	        			}
	        		}
	        		query = query+"\n"+line;
	        	}
	        } while(!done);
        }
        server.shutdownNow();
    }
}
