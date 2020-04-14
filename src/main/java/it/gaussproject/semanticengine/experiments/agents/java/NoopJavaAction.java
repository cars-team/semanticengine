package it.gaussproject.semanticengine.experiments.agents.java;

import java.util.Map;

import it.gaussproject.semanticengine.Engine;
import it.gaussproject.semanticengine.actions.Action;

public class NoopJavaAction implements Action {

	@Override
	public void execute(Engine engine, Map<String, String> actionParams, Map<String, Object> paramMap) {
	}

}
