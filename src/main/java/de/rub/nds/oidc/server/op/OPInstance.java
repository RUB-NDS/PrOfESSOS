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
import de.rub.nds.oidc.utils.ImplementationLoadException;
import de.rub.nds.oidc.utils.ImplementationLoader;
import java.util.Map;

/**
 *
 * @author Tobias Wich
 */
public class OPInstance {

	private final OPImplementation impl;

	public OPInstance(OPConfigType config, TestStepLogger logger, Map<String, ?> suiteCtx, Map<String, ?> stepCtx)
			throws ImplementationLoadException {
		impl = ImplementationLoader.loadClassInstance(config.getImplementationClass(), OPImplementation.class);
		impl.setLogger(logger);
		impl.setContext(suiteCtx, stepCtx);
		impl.setParameters(config.getParameter());
	}

	public OPImplementation getImpl() {
		return impl;
	}

}
