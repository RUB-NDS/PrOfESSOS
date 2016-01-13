/****************************************************************************
 * Copyright (C) 2016 Tobias Wich
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ***************************************************************************/

package de.rub.nds.oidc.server;

import de.rub.nds.oidc.server.op.OPInstance;
import de.rub.nds.oidc.server.rp.RPInstance;
import java.util.Map;
import java.util.TreeMap;
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
	}

	public void addOP2(String testId, ServerInstance<OPInstance> inst) {
		op2s.put(testId, inst);
	}

	public void addRP(String testId, ServerInstance<RPInstance> inst) {
		rps.put(testId, inst);
	}

	public ServerInstance<OPInstance> getOP1(String testId) throws ServerInstanceMissingException {
		ServerInstance<OPInstance> result = op1s.get(testId);
		if (result == null) {
			throw createExc("OP-1", testId);
		} else {
			return result;
		}
	}

	public ServerInstance<OPInstance> getOP2(String testId) throws ServerInstanceMissingException {
		ServerInstance<OPInstance> result = op2s.get(testId);
		if (result == null) {
			throw createExc("OP-2", testId);
		} else {
			return result;
		}
	}

	public ServerInstance<RPInstance> getRP(String testId) throws ServerInstanceMissingException {
		ServerInstance<RPInstance> result = rps.get(testId);
		if (result == null) {
			throw createExc("RP", testId);
		} else {
			return result;
		}
	}

	private ServerInstanceMissingException createExc(String name, String testId) {
		String msg = String.format("%s instance for id %s is missing in the registry.", name, testId);
		ServerInstanceMissingException ex = new ServerInstanceMissingException(msg);
		return ex;
	}

}
