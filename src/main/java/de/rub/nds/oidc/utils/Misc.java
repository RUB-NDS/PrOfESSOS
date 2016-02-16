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

package de.rub.nds.oidc.utils;

import de.rub.nds.oidc.test_model.TestStepResult;
import javax.annotation.Nullable;

/**
 *
 * @author Tobias Wich
 */
public class Misc {

	@Nullable
	public static TestStepResult getWorst(@Nullable TestStepResult r1, @Nullable TestStepResult r2) {
		if (r1 == null || r2 == null) {
			if (r1 == null) {
				return r2;
			} else {
				return r1;
			}
		} else {
			if (r1.compareTo(r2) < 0) {
				return r2;
			} else {
				return r1;
			}
		}
	}

}
