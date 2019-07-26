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

package de.rub.nds.oidc.browser;

import de.rub.nds.oidc.server.op.OPContextConstants;
import de.rub.nds.oidc.server.op.OPParameterConstants;
import de.rub.nds.oidc.server.op.OPType;
import de.rub.nds.oidc.test_model.TestStepResult;
import de.rub.nds.oidc.utils.Misc;


public class TokenSubstRPBrowser extends DefaultRPTestBrowser {

	@Override
	public TestStepResult run() throws InterruptedException {
		// run test
		TestStepResult superResult = super.run();

		// check conditions
		boolean userInfoReqFails = params.getBool(OPParameterConstants.FORCE_USERINFO_REQUEST_FAILS);
		boolean tokenReqFails = params.getBool(OPParameterConstants.FORCE_TOKEN_REQUEST_FAILS);
		OPType tokenReqReceived = (OPType) stepCtx.getOrDefault(OPContextConstants.TOKEN_REQ_RECEIVED_AT_OP_TYPE, null);
		OPType userInfoRequests = (OPType) stepCtx.getOrDefault(OPContextConstants.USERINFO_REQ_RECEIVED_AT_OP_TYPE, null);

		TestStepResult localResult = TestStepResult.PASS;
		if (tokenReqReceived != null && tokenReqFails) {
			// TODO: check flow, only fail when hybrid
			logger.log("Authorization Code redeemed for Token, assuming test failed.");
			localResult = TestStepResult.FAIL;
		}
		if (userInfoReqFails && userInfoRequests != null) {
			logger.log("Token redeemed at UserInfo endpoint, assuming test failed.");
			localResult = TestStepResult.FAIL;
		}

		return Misc.getWorst(superResult, localResult);
	}
}
