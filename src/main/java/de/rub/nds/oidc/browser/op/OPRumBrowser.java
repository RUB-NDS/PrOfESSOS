package de.rub.nds.oidc.browser.op;

import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import de.rub.nds.oidc.server.rp.RPContextConstants;
import de.rub.nds.oidc.server.rp.RPParameterConstants;
import de.rub.nds.oidc.server.rp.RPType;
import de.rub.nds.oidc.test_model.TestStepResult;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class OPRumBrowser extends AbstractOPBrowser {

    @Override
    public final TestStepResult run() throws InterruptedException {
		logger.log("OPRumeBrowser started");
        if (! (boolean) stepCtx.get(RPContextConstants.STEP_SETUP_FINISHED)) {
            logger.log("Test-setup indicates configuration error");
            return TestStepResult.UNDETERMINED;
        }

		userName = opConfig.getUser1Name();
        userPass = opConfig.getUser1Pass();

    	return runUserAuth(RPType.HONEST);
    }


    private TestStepResult runUserAuth(RPType rpType) throws InterruptedException {
		TestStepResult result = TestStepResult.NOT_RUN;
		logger.log("Starting User Authentication");
//

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

		// prepare scripts for login and consent page
		evalScriptTemplates();

		try {
			waitForPageLoad(() -> {
				driver.executeScript(submitScript);
				// capture state where the text is entered
				logScreenshot();
//				logger.log("Login Credentials entered");
				return null;
			});
//			logger.log("HTML element found in Browser.");
			// wait a bit more in case we have an angular app or some other JS heavy application
			waitMillis(2000);


			// don't run consentScript if we have already been redirected back to RP
			if (driver.getCurrentUrl().startsWith(authnReq.getRedirectionURI().toString())) {
				logger.log("No consent page encountered in browser");
			} else {
				waitForPageLoad(() -> {
					driver.executeScript(consentScript);
					logScreenshot();
					logger.log("ConsentScript executed, client authorized");
					return null;
				});
			}

		} catch (Exception e) {
			// script execution failed, likely received an error response earlier due to wrong redirect uri
			logger.log("Script execution failed, please check manually");
			logScreenshot();

			// TODO: can we always PASS the test here? in which cases should we remain UNDETERMINED?
			return TestStepResult.PASS;

		}

		String finalUrl = driver.getCurrentUrl();
		stepCtx.put(RPContextConstants.LAST_BROWSER_URL, finalUrl);
		// confirm submission of redirect uri
		blockRP.complete(null);

		// wait until RP finished processing callback
		try {
			return blockAndResult.get(5,TimeUnit.SECONDS);
		} catch (ExecutionException | TimeoutException e) {
			logger.log("Browser Timeout while waiting for RP");
			return TestStepResult.UNDETERMINED;
		}
	}

}
