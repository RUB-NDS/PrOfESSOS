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

package de.rub.nds.oidc.server.rp;

import de.rub.nds.oidc.utils.ImplementationLoader;
import de.rub.nds.oidc.test_model.RPConfigType;
import de.rub.nds.oidc.utils.ImplementationLoadException;

/**
 *
 * @author Tobias Wich
 */
public class RPInstance {

	private final RPConfigType config;
	private final RPImplementation impl;

	public RPInstance(RPConfigType config) throws ImplementationLoadException {
		this.config = config;
		impl = ImplementationLoader.loadClassInstance(config.getImplementationClass(), RPImplementation.class);
	}

	public RPImplementation getImpl() {
		return impl;
	}

}
