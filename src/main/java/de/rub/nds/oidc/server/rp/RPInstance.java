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
		impl = ImplementationLoader.loadClassInstance(config.getRPImplementationClass(), RPImplementation.class);
	}

	public RPImplementation getImpl() {
		return impl;
	}

}
