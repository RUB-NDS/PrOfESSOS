/****************************************************************************
 * Copyright 2019 Ruhr-Universität Bochum.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ***************************************************************************/

package de.rub.nds.oidc.utils;

import java.util.Map;
import java.util.Optional;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;


/**
 *
 * @author Tobias Wich
 */
public class ParamScriptExecutor {

	private final ScriptEngineManager factory;

	protected Map<String, Object> suiteCtx;
	protected Map<String, Object> stepCtx;
	protected InstanceParameters params;
	protected Object impl;

	public ParamScriptExecutor() {
		// create a script engine manager
		factory = new ScriptEngineManager();
	}

	public void setImpl(Object impl) {
		this.impl = impl;
	}

	public void setInstanceParams(InstanceParameters params) {
		this.params = params;
	}

	public void setContext(Map<String, Object> suiteCtx, Map<String, Object> stepCtx) {
		this.suiteCtx = suiteCtx;
		this.stepCtx = stepCtx;
	}

	protected ScriptEngine getEngine() {
		// create a Nashorn script engine
		ScriptEngine engine = factory.getEngineByName("nashorn");
		return engine;
	}

	public <T> Optional<Script<T>> getScript(String name) {
		return params.getScript(name)
				.map(s -> new Script() {
			@Override
			public Object exec(Map additionalParams) throws ScriptException, ClassCastException {
				// evaluate JavaScript statement
				ScriptEngine engine = getEngine();
				// add context
				engine.put("suiteCtx", suiteCtx);
				engine.put("stepCtx", stepCtx);
				engine.put("impl", impl);
				engine.put("instParams", params);
				engine.put("params", additionalParams);

				// execute
				return (T) engine.eval(s);
			}
		});
	}

}
