package it.gaussproject.semanticengine.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.ws.rs.core.MediaType;

public class RestJSON {
	@SuppressWarnings("unused")
	private static Logger LOG = Logger.getGlobal();

	public static String post(String address, String request) throws IOException {
		URL url = new URL(address);
		HttpURLConnection con = (HttpURLConnection)url.openConnection();
		con.setRequestMethod("POST");
		con.setRequestProperty("Accept", MediaType.APPLICATION_JSON);
		con.setRequestProperty("Content-Type", MediaType.APPLICATION_JSON);
		con.setDoOutput(true);
		OutputStream os = con.getOutputStream();
		byte[] bytes = request.getBytes(StandardCharsets.UTF_8);
		os.write(bytes);
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int length;
		while((length = con.getInputStream().read(buffer)) != -1) {
		    result.write(buffer, 0, length);
		}
		String responseBody = result.toString(StandardCharsets.UTF_8.name());
		return responseBody;
	}
	
	public static Map<String, String> post(String address, Map<String, String> requestMap) throws IOException {
		JsonObjectBuilder builder = Json.createObjectBuilder();
		for(String key : requestMap.keySet()) {
			builder.add(key, requestMap.get(key));
		}
		JsonObject jsonRequest = builder.build();
		URL url = new URL(address);
		HttpURLConnection con = (HttpURLConnection)url.openConnection();
		con.setRequestMethod("POST");
		con.setRequestProperty("Accept", MediaType.APPLICATION_JSON);
		con.setRequestProperty("Content-Type", MediaType.APPLICATION_JSON);
		con.setDoOutput(true);
		OutputStream os = con.getOutputStream();
		byte[] bytes = jsonRequest.toString().getBytes(StandardCharsets.UTF_8);
		os.write(bytes);
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int length;
		while((length = con.getInputStream().read(buffer)) != -1) {
		    result.write(buffer, 0, length);
		}
		String responseBody = result.toString(StandardCharsets.UTF_8.name());
		JsonObject jsonObject = Json.createReader(new StringReader(responseBody)).readObject();
		Map<String, String> valuesMap = new HashMap<>();
		for(String key : jsonObject.keySet()) {
			JsonValue jsonValue = jsonObject.getOrDefault(key, JsonValue.NULL);
			if(jsonValue != JsonValue.NULL) {
				valuesMap.put(key, jsonValue.toString());
			}
		}
		return valuesMap;
	}
}
