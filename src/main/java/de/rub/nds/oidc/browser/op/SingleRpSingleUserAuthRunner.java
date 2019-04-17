package de.rub.nds.oidc.browser.op;

import de.rub.nds.oidc.server.rp.RPContextConstants;
import de.rub.nds.oidc.test_model.TestStepResult;

public class SingleRpSingleUserAuthRunner extends AbstractOPBrowser {

	@Override
	public final TestStepResult run() throws InterruptedException {
		if (!(boolean) stepCtx.get(RPContextConstants.STEP_SETUP_FINISHED)) {
			logger.log("Test-setup indicates configuration error");
			return TestStepResult.UNDETERMINED;
		}

		userName = opConfig.getUser1Name();
		userPass = opConfig.getUser1Pass();

		return runUserAuth(getStartRpType());
	}
}
