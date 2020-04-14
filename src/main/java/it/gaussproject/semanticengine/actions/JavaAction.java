package it.gaussproject.semanticengine.actions;

import java.util.Map;
import java.util.logging.Logger;

import it.gaussproject.semanticengine.Engine;

/**
 * Action that executes Java code locally
 * 
 * @author Davide Rossi
 */
public class JavaAction implements Action {
	private static Logger LOG = Logger.getGlobal();

	/*
	 * Executes a REST invocation.
	 * Parameters are gathered from the actionParams map.
	 * All the <key, value> pairs from the returned JSON message
	 * are stored in the paramMap map for later processing.
	 */
	@Override
	public void execute(Engine engine, Map<String, String> actionParams, Map<String, Object> paramMap) {
		String classname = actionParams.get(Engine.LSA_NS+"className");
		if(classname == null) {
			throw new RuntimeException("Missing key "+Engine.LSA_NS+"className in action parameters map");
		}
		try {
			@SuppressWarnings("rawtypes")
			Class actionClass = Class.forName(classname);
			@SuppressWarnings("deprecation")
			Action action = (Action)actionClass.newInstance();
			action.execute(engine, actionParams, paramMap);
		} catch (Exception e) {
			LOG.warning(e.toString());
		}
	}
}
