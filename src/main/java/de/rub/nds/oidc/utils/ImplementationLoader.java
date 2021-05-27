/****************************************************************************
 * Copyright 2016-2019 Ruhr-Universität Bochum.
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

import java.lang.reflect.InvocationTargetException;


/**
 *
 * @author Tobias Wich
 */
public class ImplementationLoader {

	public static <T> T loadClassInstance(String clazz, Class<T> iface) throws ImplementationLoadException {
		try {
			Class<?> classInst = ImplementationLoader.class.getClassLoader().loadClass(clazz);
			Object newInstance = classInst.getConstructor().newInstance();
			return iface.cast(newInstance);
		} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException ex) {
			throw new ImplementationLoadException("Failed to load class and constructor.", ex);
		} catch (InvocationTargetException | InstantiationException ex) {
			throw new ImplementationLoadException("Failed to instantiate class.", ex);
		}
	}

}
