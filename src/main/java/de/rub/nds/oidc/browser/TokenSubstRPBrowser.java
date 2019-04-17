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
