package it.gaussproject.semanticengine.actions;

import java.util.Map;
import java.util.logging.Logger;

import it.gaussproject.semanticengine.Engine;
import it.gaussproject.semanticengine.utils.NamedFormatter;

/**
 * Action that executes a SPAQRL construct.
 * 
 * @author Davide Rossi
 */
public class SparqlUpdateAction implements Action {
	private static Logger LOG = Logger.getGlobal();

	/*
	 * Executes a SPAQRL update action.
	 * "hasCode" is extracted from the actionParams map
	 * and is used as a template for query.
	 */
	@Override
	public void execute(Engine engine, Map<String, String> actionParams, Map<String, Object> paramMap) {
		String query = actionParams.get(Engine.LSA_NS+"hasCode");
		if(query == null) {
			throw new RuntimeException("Missing key "+Engine.LSA_NS+"hasCode in action parameters map");
		}
		try {
			query = query.replace("\\\"", "\"");
			query = Engine.QUERY_PREFIXES+NamedFormatter.format(query, paramMap);
			LOG.info("SPARQLUPDATE running query");
			engine.runUpdateTransaction(query);
		} catch (Exception e) {
			LOG.warning(e.getMessage());
		}
	}

}
