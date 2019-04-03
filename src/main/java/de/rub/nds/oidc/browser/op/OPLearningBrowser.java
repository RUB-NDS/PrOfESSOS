package de.rub.nds.oidc.browser.op;

import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import de.rub.nds.oidc.server.rp.RPContextConstants;
import de.rub.nds.oidc.server.rp.RPType;
import de.rub.nds.oidc.test_model.TestStepResult;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class OPLearningBrowser extends AbstractOPBrowser {


	@Override
	public final TestStepResult run() throws InterruptedException {
		logger.log("OPLearningBrowser started");
		if (!(boolean) stepCtx.get(RPContextConstants.STEP_SETUP_FINISHED)) {
			logger.log("Test-setup indicates configuration error");
			return TestStepResult.UNDETERMINED;
		}
		Pair<String,String> user1 = new ImmutablePair<>(opConfig.getUser1Name(), opConfig.getUser1Pass());
		Pair<String,String> user2 = new ImmutablePair<>(opConfig.getUser2Name(), opConfig.getUser2Pass());
		List<Pair> users = Arrays.asList(user1, user2);

		for (RPType type : RPType.values()) {
			for (Pair entry : users) {
				userName = (String) entry.getKey();
				userPass = (String) entry.getValue();

				TestStepResult result = runUserAuth(type);
				if (result != TestStepResult.PASS) {
					logger.log(String.format("Authentication of User %s with password %s failed", userName, userPass));
					return result;
				}
				// reload browser to clear sessions
				loadDriver(true);
			}
		}

		return TestStepResult.PASS;
	}

	@Override
	protected TestStepResult runUserAuth(RPType rpType) throws InterruptedException {
//		TestStepResult result = TestStepResult.NOT_RUN;
		logger.log("run userAuth");

		// store user credentials to make them accessible to RP
		stepCtx.put(RPContextConstants.CURRENT_USER_USERNAME, userName);
		stepCtx.put(RPContextConstants.CURRENT_USER_PASSWORD, userPass);

		// prepare locks and share with RP
		CompletableFuture<?> blockRP = new CompletableFuture();
		stepCtx.put(RPContextConstants.BLOCK_RP_FOR_BROWSER_FUTURE, blockRP);
		CompletableFuture<TestStepResult> blockAndResult = new CompletableFuture<>();
		stepCtx.put(RPContextConstants.BLOCK_BROWSER_AND_TEST_RESULT, blockAndResult);

		// fetch prepared authentication request and start authentication
//		AuthenticationRequest authnReq = getAuthnReq(rpType);
		String authnReq = getAuthnReqString(rpType);
		URI honestRedirect = (URI) stepCtx.get(RPContextConstants.RP1_PREPARED_REDIRECT_URI);
		URI evilRedirect = (URI) stepCtx.get(RPContextConstants.RP2_PREPARED_REDIRECT_URI);

		// run login script
		logger.logCodeBlock(authnReq, "Authentication Request, opening browser with URL:");
		driver.get(authnReq);
		// delay form submissions to allow for taking screenshots
		// using selenium (after executeScript() returned)
		driver.executeScript(getFormSubmitDelayScript());
		waitMillis(500);

		// prepare scripts for login and consent page
		evalScriptTemplates();
		try {
			logger.logCodeBlock(submitScript, "Using Login script:");

			waitForDocumentReadyAndJsReady(() -> {
				driver.executeScript(submitScript);
				// capture state where the text is entered
				logger.log("Login Credentials entered");
				logScreenshot();
				return null;
			});
		} catch (Exception e) {
			logger.log("Execution of login script failed", e);
			logScreenshot();

			return TestStepResult.UNDETERMINED;
		}
		logger.log("HTML element found in Browser.");

		// don't run consentScript if we have already been redirected back to RP
		String location = driver.getCurrentUrl();
		if (location.startsWith(honestRedirect.toString()) || location.startsWith(evilRedirect.toString())) {
			logger.log("No consent page encountered in browser");
		} else {
			try {
				driver.executeScript(getFormSubmitDelayScript());
				logger.logCodeBlock(consentScript, "Using Consent script:");

				waitForPageLoad(() -> {
					driver.executeScript(consentScript);
					logScreenshot();
					logger.log("ConsentScript executed, client authorized.");
					return null;
				});
			} catch (Exception e) {
				logger.log("Execution of consent script failed.", e);
				logScreenshot();

				return TestStepResult.UNDETERMINED;
			}
		}

		String finalUrl = driver.getCurrentUrl();
		stepCtx.put(RPContextConstants.LAST_BROWSER_URL, finalUrl);
		// confirm submission of redirect uri
		blockRP.complete(null);

		// wait until RP finished processing callback

		// take a screenshot again to show the finished site
		logger.log("Last URL seen in Browser: " + finalUrl);
		logScreenshot();


		try {
			return blockAndResult.get(10, TimeUnit.SECONDS);
		} catch (ExecutionException | TimeoutException e) {
			logger.log("Browser Timeout while waiting for RP");
			return TestStepResult.UNDETERMINED;
		}
	}

}
