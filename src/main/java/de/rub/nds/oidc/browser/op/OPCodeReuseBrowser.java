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
import de.rub.nds.oidc.server.rp.RPParameterConstants;
import de.rub.nds.oidc.server.rp.RPType;
import de.rub.nds.oidc.test_model.TestStepResult;

import java.util.ArrayList;


public class OPCodeReuseBrowser extends AbstractOPBrowser {

	@Override
	public final TestStepResult run() throws InterruptedException {
		logger.log("OPCodeReuseBrowser started");
		if (!(boolean) stepCtx.get(RPContextConstants.STEP_SETUP_FINISHED)) {
			logger.log("Test-setup indicates configuration error");
			return TestStepResult.UNDETERMINED;
		}

		TestStepResult result = null;
		ArrayList<String[]> users = new ArrayList<>();
		users.add(new String[]{opConfig.getUser1Name(), opConfig.getUser1Pass()});
		users.add(new String[]{opConfig.getUser2Name(), opConfig.getUser2Pass()});

		for (int i = 0; i < 2; i++) {
			userName = users.get(i)[0];
			userPass = users.get(i)[1];

			boolean isSingleRP = Boolean.valueOf((String) stepCtx.get(RPParameterConstants.IS_SINGLE_RP_TEST));
			RPType type = isSingleRP ? RPType.HONEST : (i == 1) ? RPType.EVIL : RPType.HONEST;

			result = runUserAuth(type);

			// reload browser to clear sessions
			reloadDriver();
		}
		return result;
	}

}
