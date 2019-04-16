package de.rub.nds.oidc.browser.op;

import de.rub.nds.oidc.server.rp.RPContextConstants;
import de.rub.nds.oidc.server.rp.RPType;
import de.rub.nds.oidc.test_model.TestStepResult;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class OPLearningBrowser extends MultiRpMultiUserAuthRunner {

	@Override
	protected TestStepResult runUserAuth(RPType rpType) throws InterruptedException {
		// store user credentials to make them accessible to RP
		stepCtx.put(RPContextConstants.CURRENT_USER_USERNAME, userName);
		stepCtx.put(RPContextConstants.CURRENT_USER_PASSWORD, userPass);
		// prepare locks and share with RP
		initRPLocks();

		logger.log(String.format("Start user authentication for %s with username: %s", getClientName(rpType), userName));
		// fetch prepared authentication request and start authentication
		String authnReq = getAuthnReqString(rpType);
		URI honestRedirect = (URI) stepCtx.get(RPContextConstants.RP1_PREPARED_REDIRECT_URI);
		URI evilRedirect = (URI) stepCtx.get(RPContextConstants.RP2_PREPARED_REDIRECT_URI);

		// run login script
		logger.logCodeBlock("Authentication Request, opening browser with URL:", authnReq);
		driver.get(authnReq);
		// delay form submissions to allow for taking screenshots
		// using selenium (after executeScript() returned)
		driver.executeScript(getFormSubmitDelayScript());
		waitMillis(500);

		// prepare scripts for login and consent page
		evalScriptTemplates();
		try {
			logger.logCodeBlock("Using Login script:", submitScript);

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
				logger.logCodeBlock("Using Consent script:", consentScript);

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
		
		confirmBrowserFinished();
		logScreenshot();

		try {
			return blockAndResult.get(10, TimeUnit.SECONDS);
		} catch (ExecutionException | TimeoutException e) {
			logger.log("Browser Timeout while waiting for RP");
			return TestStepResult.UNDETERMINED;
		}
	}

}
