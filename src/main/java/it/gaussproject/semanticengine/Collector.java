package it.gaussproject.semanticengine;

import java.util.logging.Logger;

public class Collector implements Runnable {
	@SuppressWarnings("unused")
	private static Logger LOG = Logger.getGlobal();

	private Engine engine;
	private int memoryRatio = -1;
	private int runs = 0;
	private long totalTimeNS = 0L;
	private long delay = 50000;

	public Collector(Engine engine, int memoryRatio, long delay) {
		this(engine, memoryRatio);
		this.delay = delay;
	}

	public Collector(Engine engine, int memoryRatio) {
		this.engine = engine;
		this.memoryRatio = memoryRatio;
	}

	@Override
	public void run() {
		String query = 
			"DELETE { ?s ?p ?o } WHERE {\n" + 
			"    ?s <http://www.gauss.it/lsa/expires> ?e .\n" + 
			"    ?s ?p ?o .\n" + 
			"    filter (?e < now()) .\n" + 
			"}";
		for(;;) {
			try {
				Thread.sleep(this.delay);
			} catch (InterruptedException e) {
				return;
			}
			long startTimeNS = System.nanoTime();
			if(memoryRatio <= 0 || Runtime.getRuntime().freeMemory() < Runtime.getRuntime().totalMemory()/5) {
				long prevSize = engine.size();
				this.engine.runUpdateTransaction(query);
				long elapsedTimeNS = System.nanoTime() - startTimeNS;
				this.runs++;
				this.totalTimeNS += elapsedTimeNS;
				this.engine.logStat(Collector.class.getSimpleName(), 
						"Latest run - prev size: "+prevSize+
						" new size: "+engine.size()+
						" time used: "+((elapsedTimeNS/1000)/1000.0)+"ms"+
						" avg time: "+((this.totalTimeNS/this.runs/1000)/1000.0)+"ms");
			}
		}
	}

}
