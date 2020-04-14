package it.gaussproject.semanticengine.experiments.agents.java;

import java.text.ParseException;
import java.util.Map;
import java.util.logging.Logger;

import it.gaussproject.semanticengine.Engine;
import it.gaussproject.semanticengine.actions.Action;
import it.gaussproject.semanticengine.utils.TurtleLiteralParser;

public class TimingJavaAction implements Action {
	@SuppressWarnings("unused")
	private static Logger LOG = Logger.getGlobal();

	private static long totalTimeNS = 0L;
	private static int callCounter = 0;
	
	@Override
	public void execute(Engine engine, Map<String, String> actionParams, Map<String, Object> paramMap) throws ParseException {	
		TimingJavaAction.callCounter++;
		String observation_creationTS = (String)paramMap.get("_observation_creationTS");
		long elapsedTime = System.nanoTime() - TurtleLiteralParser.parse(observation_creationTS).getLong();
		TimingJavaAction.totalTimeNS += elapsedTime;
		engine.logStat(TimingJavaAction.class.getSimpleName(),
				"Activation count: "+TimingJavaAction.callCounter+
				" average activation time: "+((TimingJavaAction.totalTimeNS/TimingJavaAction.callCounter/1000)/1000.0)+"ms");
	}

}
