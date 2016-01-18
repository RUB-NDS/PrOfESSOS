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

package de.rub.nds.oidc.server;

import de.rub.nds.oidc.log.TestStepLogger;

/**
 *
 * @author Tobias Wich
 * @param <T>
 */
public class ServerInstance <T> {

	private final T inst;
	private final TestStepLogger logger;

	public ServerInstance(T inst, TestStepLogger logger) {
		this.inst = inst;
		this.logger = logger;
	}

	public T getInst() {
		return inst;
	}

}
