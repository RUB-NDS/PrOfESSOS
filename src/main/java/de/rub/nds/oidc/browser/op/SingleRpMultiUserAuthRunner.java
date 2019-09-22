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
import de.rub.nds.oidc.test_model.TestStepResult;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;


public class SingleRpMultiUserAuthRunner extends AbstractOPBrowser {

	@Override
	public final TestStepResult run() throws InterruptedException {
		if (!(boolean) stepCtx.get(RPContextConstants.STEP_SETUP_FINISHED)) {
			logger.log("Test-setup indicates configuration error");
			return TestStepResult.UNDETERMINED;
		}

		Pair<String, String> user1 = new ImmutablePair<>(opConfig.getUser1Name(), opConfig.getUser1Pass());
		Pair<String, String> user2 = new ImmutablePair<>(opConfig.getUser2Name(), opConfig.getUser2Pass());
		List<Pair> users = Arrays.asList(user1, user2);

		TestStepResult result = TestStepResult.PASS;
		for (Pair entry : users) {
			userName = (String) entry.getKey();
			userPass = (String) entry.getValue();

			result = runUserAuth(getStartRpType());
			if (result != TestStepResult.PASS) {
//				logger.log(String.format("Authentication of User %s with password %s failed", userName, userPass));
				return result;
			}
			// reload browser to clear sessions
			reloadDriver();
		}

		return result;
	}
}
