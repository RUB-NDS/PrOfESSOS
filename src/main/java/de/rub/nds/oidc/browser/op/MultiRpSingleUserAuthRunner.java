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
			loadDriver(true);
		}

		return TestStepResult.PASS;
	}
}
