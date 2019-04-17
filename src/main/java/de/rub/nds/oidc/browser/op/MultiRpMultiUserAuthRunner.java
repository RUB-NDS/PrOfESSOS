package de.rub.nds.oidc.browser.op;

import de.rub.nds.oidc.server.rp.RPContextConstants;
import de.rub.nds.oidc.server.rp.RPType;
import de.rub.nds.oidc.test_model.TestStepResult;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;

public class MultiRpMultiUserAuthRunner extends AbstractOPBrowser {

	@Override
	public final TestStepResult run() throws InterruptedException {
		if (!(boolean) stepCtx.get(RPContextConstants.STEP_SETUP_FINISHED)) {
			logger.log("Test-setup indicates configuration error");
			return TestStepResult.UNDETERMINED;
		}
		Pair<String, String> user1 = new ImmutablePair<>(opConfig.getUser1Name(), opConfig.getUser1Pass());
		Pair<String, String> user2 = new ImmutablePair<>(opConfig.getUser2Name(), opConfig.getUser2Pass());
		List<Pair> users = Arrays.asList(user1, user2);

		RPType first = getStartRpType();
		RPType second = first.equals(RPType.HONEST) ? RPType.EVIL : RPType.HONEST;

		for (RPType type : Arrays.asList(first, second)) {
			for (Pair entry : users) {
				userName = (String) entry.getKey();
				userPass = (String) entry.getValue();

				TestStepResult result = runUserAuth(type);
				if (result != TestStepResult.PASS) {
					if (Boolean.parseBoolean((String) stepCtx.get(RPContextConstants.IS_RP_LEARNING_STEP))) {
						// in learning phase, all steps muss succeed
						logger.log(String.format("Authentication of User %s with password %s failed", userName, userPass));
						return result;
					}
				}
				// reload browser to clear sessions
				loadDriver(true);
			}
		}

		return TestStepResult.PASS;
	}
}
