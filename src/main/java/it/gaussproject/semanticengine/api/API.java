package it.gaussproject.semanticengine.api;

import java.io.StringReader;
import java.text.ParseException;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import it.gaussproject.semanticengine.Engine;

/**
 * Root resource (exposes an "api" path)
 */
@Path("api")
public class API {
	@SuppressWarnings("unused")
	private static Logger LOG = Logger.getGlobal();
	
	@Context
    Configuration configuration;

	protected Engine getEngine() {
		return (Engine)configuration.getProperty(Engine.ENGINE_RESOURCE_NAME);
	}
	
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response listKnowledgeBase() {
    	try {
	    	ResultSet results = getEngine().runSelectTransaction("SELECT * WHERE { ?x ?y ?z }");
	    	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	    	ResultSetFormatter.outputAsCSV(byteArrayOutputStream, results);
	    	return Response.ok(new String(byteArrayOutputStream.toByteArray())).build();
    	} catch(Exception e) {
    		return Response.status(500).entity(e.toString()).build();
    	}
    }

    @Path("size")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response getKBSize() {
    	return Response.ok(getEngine().size()).build();
    }
    
    @Path("selects")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Response runSelect(String query) {
    	ResultSet resultSet = getEngine().runSelectTransaction(query);    	
    	return Response.ok(ResultSetFormatter.asText(resultSet)).build();
    }
    
    @Path("updates")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Response runUpdate(String query) {
    	getEngine().runUpdateTransaction(query);
    	return Response.ok("done").build();
    }
    
    @Path("constructs")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Response runContruct(String query) {
    	Model model = getEngine().runConstructTransaction(query);
    	ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    	RDFDataMgr.write(outputStream, model, Lang.TURTLE);
    	return Response.ok(outputStream.toString(java.nio.charset.StandardCharsets.UTF_8)).build();
    }
    
    /**
     * POST endpoint to notify an observation.
     * The request message is a JSON document with the following
     * properties:
     *   madeBySensor
     *   observedProperty
     *   hasFeatureOfInterest
     *   observationSubtype?
     *   resultTime?
     *   hasSimpleResult?
     */
    @Path("observations")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @POST
    public Response createObservation(String json) throws ParseException {
		JsonObject jsonObject = Json.createReader(new StringReader(json)).readObject();
		if(jsonObject.getJsonString("nowait") == null) {
			getEngine().waitQueue();
		}
		String resultTime = ApiUtils.addJsonObservation(getEngine(), jsonObject);
		
		JsonObject responseJson = Json.createObjectBuilder().add("created", "").add("resultTime", resultTime).build();
    	return Response.ok(responseJson.toString()).build();
    }
}
