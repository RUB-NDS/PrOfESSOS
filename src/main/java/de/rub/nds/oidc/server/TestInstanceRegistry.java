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

import de.rub.nds.oidc.server.op.OPInstance;
import de.rub.nds.oidc.server.rp.RPInstance;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.enterprise.context.ApplicationScoped;

/**
 *
 * @author Tobias Wich
 */
@ApplicationScoped
public class TestInstanceRegistry {

	private final Map<String, ServerInstance<OPInstance>> op1s;
	private final Map<String, ServerInstance<OPInstance>> op2s;
	private final Map<String, ServerInstance<RPInstance>> rps;

	public TestInstanceRegistry() {
		this.op1s = new TreeMap<>();
		this.op2s = new TreeMap<>();
		this.rps = new TreeMap<>();
	}

	public void addOP1(String testId, ServerInstance<OPInstance> inst) {
		op1s.put(testId, inst);
		inst.getInst().getImpl().setTestId(testId);
	}

	public void addOP2(String testId, ServerInstance<OPInstance> inst) {
		op2s.put(testId, inst);
	}

	public void addRP(String testId, ServerInstance<RPInstance> inst) {
		rps.put(testId, inst);
	}

	public void removeOP1(String testId) {
		op1s.remove(testId);
	}

	public void removeOP2(String testId) {
		op2s.remove(testId);
	}

	public void removeRP(String testId) {
		rps.remove(testId);
	}

	private <T> Supplier<ServerInstance<T>> getInstance(Map<String, ServerInstance<T>> reg, String testId) {
		return () -> reg.get(testId);
	}

	private <T> Function<String, ServerInstance<T>> getInstances(Map<String, ServerInstance<T>> reg) {
		return (testId) -> reg.get(testId);
	}

	public ServerInstance<OPInstance> getOP1(String testId) throws ServerInstanceMissingException {
		ServerInstance<OPInstance> result = getOP1Supplier().apply(testId);
		if (result == null) {
			throw createException("OP-1", testId);
		} else {
			return result;
		}
	}

	public Function<String, ServerInstance<OPInstance>> getOP1Supplier() {
		return getInstances(op1s);
	}

	public ServerInstance<OPInstance> getOP2(String testId) throws ServerInstanceMissingException {
		ServerInstance<OPInstance> result = getOP2Supplier().apply(testId);
		if (result == null) {
			throw createException("OP-2", testId);
		} else {
			return result;
		}
	}

	public Function<String, ServerInstance<OPInstance>> getOP2Supplier() {
		return getInstances(op2s);
	}

	public ServerInstance<RPInstance> getRP(String testId) throws ServerInstanceMissingException {
		ServerInstance<RPInstance> result = getRPSupplier().apply(testId);
		if (result == null) {
			throw createException("RP", testId);
		} else {
			return result;
		}
	}

	public Function<String, ServerInstance<RPInstance>> getRPSupplier() {
		return getInstances(rps);
	}

	private ServerInstanceMissingException createException(String name, String testId) {
		String msg = String.format("%s instance for id %s is missing in the registry.", name, testId);
		ServerInstanceMissingException ex = new ServerInstanceMissingException(msg);
		return ex;
	}

}
