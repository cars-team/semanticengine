package it.gaussproject.semanticengine.actions;

import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;

import it.gaussproject.semanticengine.Engine;
import it.gaussproject.semanticengine.utils.NamedFormatter;

/**
 * Action that executes a SPAQRL query.
 * 
 * @author Davide Rossi
 */
public class SparqlQueryAction implements Action {
	private static Logger LOG = Logger.getGlobal();

	/*
	 * Executes a SPAQRL query action.
	 * "hasCode" is extracted from the actionParams map
	 * and is used as a template for query.
	 * Fills the paramMap with <key, value> pairs taken from the variables returned by the query.
	 */
	@Override
	public void execute(Engine engine, Map<String, String> actionParams, Map<String, Object> paramMap) {
		String query = actionParams.get(Engine.LSA_NS+"hasCode");
		if(query != null) {
			query = NamedFormatter.format(query.replace("\\\"", "\""), paramMap);
		}
		if(query != null && query.trim().length() > 0) {
			LOG.info("Executing query "+query);
			ResultSet resultSet = engine.runSelectTransaction(query);
			if(resultSet.hasNext()) {
				QuerySolution querySolution = resultSet.next();
				Iterator<String > propsIterator = querySolution.varNames();
				while(propsIterator.hasNext()) {
					String propName = propsIterator.next();
					String propValue = querySolution.get(propName).toString();
					LOG.info("Adding variable "+propName+" to the parameters map");
					paramMap.put(propName, propValue);
				}
			}
		}
	}
}
