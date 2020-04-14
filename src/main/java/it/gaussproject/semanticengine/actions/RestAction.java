package it.gaussproject.semanticengine.actions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.MediaType;

import it.gaussproject.semanticengine.Engine;
import it.gaussproject.semanticengine.api.ApiUtils;
import it.gaussproject.semanticengine.utils.NamedFormatter;

/**
 * Action that executes a REST invocation.
 * JSON is the only supported content type.
 * 
 * @author Davide Rossi
 */
public class RestAction implements Action {
	private static Logger LOG = Logger.getGlobal();

	/*
	 * Executes a REST invocation.
	 * Parameters are gathered from the actionParams map.
	 * All the <key, value> pairs from the returned JSON message
	 * are stored in the paramMap map for later processing.
	 */
	@Override
	public void execute(Engine engine, Map<String, String> actionParams, Map<String, Object> paramMap) {
		String method = actionParams.get(Engine.LSA_NS+"hasMethod");
		if(method == null) {
			method = "POST";
		}
		String address = actionParams.get(Engine.LSA_NS+"hasURL");
		if(address == null) {
			throw new RuntimeException("Missing key "+Engine.LSA_NS+"hasURL in action parameters map");
		}
		//if address contains ";" it is a list of addresses, choose one randomly (to balance the load)
		if(address.contains(";")) {
			String[] addresses = address.split(";");
			address = addresses[(int)(Math.random()*addresses.length)];
		}
		String requestBody = actionParams.get(Engine.LSA_NS+"requestJSON");
		if(requestBody != null) {
			requestBody = NamedFormatter.format(requestBody.replace("\\\"", "\""), paramMap);
		}
		try {
			URL url = new URL(address);
			HttpURLConnection connection = (HttpURLConnection)url.openConnection();
			connection.setRequestMethod(method);
			connection.setRequestProperty("Accept", MediaType.APPLICATION_JSON);
			if(method.equals("POST")) {
				connection.setRequestProperty("Content-Type", MediaType.APPLICATION_JSON);
				connection.setDoOutput(true);
				OutputStream os = connection.getOutputStream();
				byte[] bytes = requestBody.getBytes(StandardCharsets.UTF_8);
				os.write(bytes);
			}
			ByteArrayOutputStream result = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int length;
			while((length = connection.getInputStream().read(buffer)) != -1) {
			    result.write(buffer, 0, length);
			}
			String responseBody = result.toString(StandardCharsets.UTF_8.name());
			LOG.info("REST: "+method+" to "+address+" with body: "+requestBody+" returned: "+responseBody);
			JsonObject jsonResponse = Json.createReader(new StringReader(responseBody)).readObject();
			if(jsonResponse.containsKey("observation")) {
				JsonObject jsonObservation = jsonResponse.getJsonObject("observation");
				ApiUtils.addJsonObservation(engine, jsonObservation);
				jsonResponse = Json.createObjectBuilder(jsonResponse).remove("observation").build();
			}
			for(String key : jsonResponse.keySet()) {
				JsonValue jsonValue = jsonResponse.getOrDefault(key, JsonValue.NULL);
				if(jsonValue != JsonValue.NULL) {
					paramMap.put(key, jsonValue.toString());
				}
			}
		} catch (IOException e) {
			LOG.warning(e.toString());
		}
	}

}
