package it.gaussproject.semanticengine.actions;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import it.gaussproject.semanticengine.Engine;
import it.gaussproject.semanticengine.utils.NamedFormatter;

/**
 * Action that executes a SPAQRL construct.
 * 
 * @author Davide Rossi
 */
public class SparqlConstructAction implements Action {
	private static Logger LOG = Logger.getGlobal();

	/*
	 * Executes a SPAQRL construct action.
	 * "hasCode" is extracted from the actionParams map
	 * and is used as a template for query.
	 */
	@Override
	public void execute(Engine engine, Map<String, String> actionParams, final Map<String, Object> paramMap) {
		String query = actionParams.get(Engine.LSA_NS+"hasCode");
		query = query.replace("\\\"", "\"");
		query = Engine.QUERY_PREFIXES+NamedFormatter.format(query, paramMap);
		LOG.info("SPARQLCONSTRUCT running query: "+query);
		Model newModel = engine.runConstructTransaction(query);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		RDFDataMgr.write(baos, newModel, Lang.TURTLE);
		LOG.info("Result: "+baos.toString(Charset.defaultCharset()));
		engine.runAddTransaction(newModel);
	}

}
