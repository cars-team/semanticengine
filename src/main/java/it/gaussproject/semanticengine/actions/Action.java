package it.gaussproject.semanticengine.actions;

import java.util.Map;

import it.gaussproject.semanticengine.Engine;

public interface Action {
	public void execute(Engine engine, Map<String, String> actionParams, Map<String, Object> paramMap) throws Exception;
}
