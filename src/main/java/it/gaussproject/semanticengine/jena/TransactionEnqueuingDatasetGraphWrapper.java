package it.gaussproject.semanticengine.jena;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.TransactionHandler;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphWrapper;
import org.apache.jena.sparql.graph.GraphWrapper;

/**
 * This class wraps a DatasetGraph in order to accumulate triples added to
 * its defaultGraph. When a transaction is committed all these triples
 * are put in a model and added to the queue given in the constructor
 * where they can be retrieved for further processing.
 * In order to intercept new triples when added to the defaultGraph
 * we wrap it too and redefine DatasetGraph's getDefaultGraph
 * to return a wrapped graph (that intercepts Graph.add(Triple t)).
 * 
 * @author Davide Rossi
 */
public class TransactionEnqueuingDatasetGraphWrapper extends DatasetGraphWrapper {
	private static Logger LOG = Logger.getGlobal();

	private class ModelEnqueuingGraphWrapper extends GraphWrapper {
		private Model accumulatorModel = ModelFactory.createDefaultModel();

		public ModelEnqueuingGraphWrapper(Graph graph) {
			super(graph);
		}
		public synchronized Model takeChanges() {
			Model currentModel = this.accumulatorModel;
			this.accumulatorModel = ModelFactory.createDefaultModel();
			return currentModel;
		}
		@Override
		public void add(Triple triple) throws AddDeniedException {
			LOG.info("adding "+triple);
			super.add(triple);
			this.accumulatorModel.getGraph().add(triple);
		}
		@Override
		public TransactionHandler getTransactionHandler() {
			return super.getTransactionHandler();
		}
	}
	
	private ModelEnqueuingGraphWrapper modelEnqueuingGraphWrapper;
	private BlockingQueue<Model> queue;
	private Map<Node, Graph> wrappedGraphs = new HashMap<Node, Graph>();
	public TransactionEnqueuingDatasetGraphWrapper(DatasetGraph datasetGraph, BlockingQueue<Model> queue) {
		super(datasetGraph);
		this.queue = queue;
	}
	@Override
	public synchronized Graph getDefaultGraph() {
		if(this.modelEnqueuingGraphWrapper == null) {
			this.modelEnqueuingGraphWrapper = new ModelEnqueuingGraphWrapper(super.getDefaultGraph());
		}
		return this.modelEnqueuingGraphWrapper;
	}
	@Override
	public synchronized Graph getGraph(Node graphNode) {
		Graph wrappedGraph = this.wrappedGraphs.get(graphNode);
		if(wrappedGraph == null) {
			Graph graph = super.getGraph(graphNode);
			if(graph != null) {
				wrappedGraph = new ModelEnqueuingGraphWrapper(graph);
				this.wrappedGraphs.put(graphNode, wrappedGraph);
			}
		}
		return wrappedGraph;
	}
	@Override
	public void commit() {
		super.commit();
		if(this.modelEnqueuingGraphWrapper != null) {
			Model model = this.modelEnqueuingGraphWrapper.takeChanges();
			if(!model.isEmpty()) {
				this.queue.add(model);
			}
		}
	}
}