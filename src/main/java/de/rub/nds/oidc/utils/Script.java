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

import java.util.Collections;
import java.util.Map;
import javax.script.ScriptException;


/**
 *
 * @author Tobias Wich
 * @param <T> Rewsult type of the script.
 */
public interface Script <T> {

	default T exec() throws ScriptException, ClassCastException {
		return exec(Collections.emptyMap());
	}

	default T execSafe() {
		return execSafe(Collections.emptyMap());
	}

	default T execSafe(Map<String, Object> additionalParams) {
		try {
			return exec(additionalParams);
		} catch (ClassCastException | ScriptException ex) {
			throw new RuntimeException("Error executing requested script", ex);
		}
	}

	T exec(Map<String, Object> additionalParams) throws ScriptException, ClassCastException;

}
