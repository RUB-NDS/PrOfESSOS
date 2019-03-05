package de.rub.nds.oidc.browser.op;

import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import de.rub.nds.oidc.server.rp.RPContextConstants;
import de.rub.nds.oidc.server.rp.RPType;
import de.rub.nds.oidc.test_model.TestStepResult;

import java.util.HashMap;
import java.util.Map;
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

		HashMap<String, String> users = new HashMap<>();
		users.put(opConfig.getUser1Name(), opConfig.getUser1Pass());
		users.put(opConfig.getUser2Name(), opConfig.getUser2Pass());

		for (RPType type : RPType.values()) {
			for (Map.Entry<String, String> entry : users.entrySet()) {
				userName = entry.getKey();
				userPass = entry.getValue();

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

		// build authnReq and call in browser
		AuthenticationRequest authnReq = getAuthnReq(rpType);

		// run login script
		logger.log(String.format("AuthnReq: opening browser with URL '%s'.", authnReq.toURI().toString()));
		driver.get(authnReq.toURI().toString());
		// delay form submissions for screenshots
		driver.executeScript(getFormSubmitDelayScript());
		waitMillis(500);

		// prepare scripts for login and consent page
		evalScriptTemplates();
		logger.log(String.format("Using Login script:%n %s", submitScript));
		// wait until a new html element appears, indicating a page load
		waitForPageLoad(() -> {
			driver.executeScript(submitScript);
			// capture state where the text is entered
			logScreenshot();
			logger.log("Login Credentials entered");
			return null;
		});
		logger.log("HTML element found in Browser.");
		// wait a bit more in case we have an angular app or some other JS heavy application
		waitMillis(1000);

		// don't run consentScript if we have already been redirected back to RP
		if (driver.getCurrentUrl().startsWith(authnReq.getRedirectionURI().toString())) {
			logger.log("No consent page encountered in browser");
		} else {
			driver.executeScript(getFormSubmitDelayScript());

			waitForPageLoad(() -> {
				driver.executeScript(consentScript);
				logScreenshot();
				logger.log("ConsentScript executed, client authorized");
				return null;
			});
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
			return blockAndResult.get(5, TimeUnit.SECONDS);
		} catch (ExecutionException | TimeoutException e) {
			logger.log("Browser Timeout while waiting for RP");
			return TestStepResult.UNDETERMINED;
		}
	}

}
