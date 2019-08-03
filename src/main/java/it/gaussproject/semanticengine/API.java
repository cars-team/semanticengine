package it.gaussproject.semanticengine;

import java.io.StringReader;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

/**
 * Root resource (exposed at "api" path)
 */
@Path("api")
public class API {
	private static Logger LOG = Logger.getGlobal();

	private Engine engine = Engine.getEngine();

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response listKnowledgeBase() {
    	ResultSet results = engine.runSelectTransaction("SELECT * WHERE { ?x ?y ?z }");
    	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    	ResultSetFormatter.outputAsCSV(byteArrayOutputStream, results);
    	return Response.ok(new String(byteArrayOutputStream.toByteArray())).build();
    }
    
    @Path("selects")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Response runSelect(String query) {
    	ResultSet resultSet = engine.runSelectTransaction(query);    	
    	return Response.ok(ResultSetFormatter.asText(resultSet)).build();
    }
    
    @Path("updates")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Response runUpdate(String query) {
    	engine.runUpdateTransaction(query);
    	return Response.ok("done").build();
    }
    
    @Path("constructs")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Response runContruct(String query) {
    	Model model = engine.runConstructTransaction(query);
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
     *   resultTime?
     *   hasSimpleResult?
     * The output message is a text document containing the TTL of the produced observation.
     * @param json
     * @return
     */
    @Path("observations")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @POST
    public Response createObservation(String json) {
		JsonObject jsonObject = Json.createReader(new StringReader(json)).readObject();
		String madeBySensor = jsonObject.getString("madeBySensor");
		String resultTime = jsonObject.getString("resultTime", null);
		if(resultTime == null) {
			resultTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(new Date());
		}
		String observedProperty = jsonObject.getString("observedProperty");
		String hasFeatureOfInterest = jsonObject.getString("hasFeatureOfInterest");
		String hasSimpleResult = jsonObject.getString("hasSimpleResult", null);

		String ttl = MessageFormat.format(Engine.ttlPrefixes+"<observations/{2}#{1}> rdf:type sosa:Observation ;\n" + 
				"  sosa:madeBySensor <{0}> ;\n" + 
				"  sosa:resultTime \"{1}\"^^xsd:dateTime ;\n" + 
				"  sosa:observedProperty <{2}> ;\n" + 
				(hasSimpleResult == null ? "" : "  sosa:hasSimpleResult {4} ;\n") + 
				"  sosa:hasFeatureOfInterest <{3}> .",
				madeBySensor, resultTime, observedProperty, hasFeatureOfInterest, hasSimpleResult);
		LOG.info("API: creating observation: \n"+ttl);
		Model newModel = ModelFactory.createDefaultModel().read(new StringReader(ttl), null, "TTL");
		engine.runAddTransaction(newModel);

		return Response.ok(ttl, MediaType.TEXT_PLAIN).build();
    }
}
