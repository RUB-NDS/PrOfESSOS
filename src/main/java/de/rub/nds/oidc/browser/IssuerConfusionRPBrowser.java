package de.rub.nds.oidc.browser;

import de.rub.nds.oidc.server.op.OPContextConstants;
import de.rub.nds.oidc.server.op.OPType;
import de.rub.nds.oidc.test_model.TestStepResult;


public class IssuerConfusionRPBrowser extends DefaultRPTestBrowser {

	@Override
	protected TestStepResult checkConditionAfterLogin() {
		OPType tokenRequestReceivedAt = (OPType) stepCtx.get(OPContextConstants.TOKEN_REQ_RECEIVED_AT_OP_TYPE);

		if (OPType.HONEST.equals(tokenRequestReceivedAt)) {
			logger.log("TokenRequest received at HonestOP, although not specified in malicious Discovery response");
			logger.log("Assuming test passed");
			return TestStepResult.PASS;
		}
		return null;
	}
}
