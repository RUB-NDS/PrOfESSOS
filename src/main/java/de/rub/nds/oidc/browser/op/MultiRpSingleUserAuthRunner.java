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

package de.rub.nds.oidc.browser.op;

import de.rub.nds.oidc.server.rp.RPContextConstants;
import de.rub.nds.oidc.server.rp.RPType;
import de.rub.nds.oidc.test_model.TestStepResult;

import java.util.Arrays;


public class MultiRpSingleUserAuthRunner extends AbstractOPBrowser {

	@Override
	public final TestStepResult run() throws InterruptedException {
		if (!(boolean) stepCtx.get(RPContextConstants.STEP_SETUP_FINISHED)) {
			logger.log("Test-setup indicates configuration error");
			return TestStepResult.UNDETERMINED;
		}
		RPType first = getStartRpType();
		RPType second = first.equals(RPType.HONEST) ? RPType.EVIL : RPType.HONEST;

		for (RPType type : Arrays.asList(first, second)) {
			userName = opConfig.getUser1Name();
			userPass = opConfig.getUser1Pass();

			TestStepResult result = runUserAuth(type);
			if (result != TestStepResult.PASS) {
//				logger.log(String.format("Authentication of User %s with password %s failed", userName, userPass));
				return result;
			}
			// reload browser to clear sessions
			reloadDriver();
		}

		return TestStepResult.PASS;
	}
}
