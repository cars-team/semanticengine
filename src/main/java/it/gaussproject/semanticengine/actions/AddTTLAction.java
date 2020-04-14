package it.gaussproject.semanticengine.actions;

import java.util.Map;
import java.util.logging.Logger;

import it.gaussproject.semanticengine.Engine;
import it.gaussproject.semanticengine.utils.NamedFormatter;

/**
 * Action that adds a TTL model to the KB.
 * 
 * @author Davide Rossi
 */
public class AddTTLAction implements Action {
	private static Logger LOG = Logger.getGlobal();

	/*
	 * Executes a add TTL action.
	 * "hasCode" is extracted from the actionParams map
	 * and is used as a template for model.
	 */
	@Override
	public void execute(Engine engine, Map<String, String> actionParams, Map<String, Object> paramMap) {
		String ttlTemplate = actionParams.get(Engine.LSA_NS+"hasCode");
		//TODO this is a workaround, find the correct way to handle escaped quotes in long strings in turtle
		String ttl = NamedFormatter.format(ttlTemplate.replace("\\\"", "\""), paramMap);
		LOG.info("Inserting model: \n"+ttl);
		engine.runAddTTLTransaction(ttl);
	}
}
