/****************************************************************************
 * Copyright 2016 Ruhr-Universität Bochum.
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

package de.rub.nds.oidc.server.op;

import de.rub.nds.oidc.log.TestStepLogger;
import de.rub.nds.oidc.test_model.OPConfigType;
import de.rub.nds.oidc.test_model.ParameterType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * @author Tobias Wich
 */
public abstract class AbstractOPImplementation implements OPImplementation {

	protected OPConfigType cfg;
	protected TestStepLogger logger;
	protected Map<String, ?> suiteCtx;
	protected Map<String, ?> stepCtx;
	protected Map<String, String> params;

	@Override
	public void setConfig(OPConfigType cfg) {
		this.cfg = cfg;
	}

	@Override
	public void setLogger(TestStepLogger logger) {
		this.logger = logger;
	}

	@Override
	public void setContext(Map<String, ?> suiteCtx, Map<String, ?> stepCtx) {
		this.suiteCtx = suiteCtx;
		this.stepCtx = stepCtx;
	}

	@Override
	public void setParameters(List<ParameterType> params) {
		this.params = params.stream()
				.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
	}

}
