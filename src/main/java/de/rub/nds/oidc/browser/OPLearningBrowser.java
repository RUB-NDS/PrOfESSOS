package de.rub.nds.oidc.browser;

import com.google.common.base.Strings;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import de.rub.nds.oidc.server.rp.RPContextConstants;
import de.rub.nds.oidc.server.rp.RPType;
import de.rub.nds.oidc.test_model.TestStepResult;
import org.apache.commons.io.IOUtils;
import org.stringtemplate.v4.ST;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class OPLearningBrowser extends BrowserSimulator {

    private String submitScript;
    private String consentScript;
	private String userName;
	private String userPass;

    @Override
    public final TestStepResult run() throws InterruptedException {
		logger.log("OPLearningBrowser started");
        if (! (boolean) stepCtx.get(RPContextConstants.STEP_SETUP_FINISHED)) {
            logger.log("Test-setup indicates configuration error");
            return TestStepResult.UNDETERMINED;
        }

        for (RPType type : RPType.values()) {
			// test user login at Honest-Client
			userName = opConfig.getUser1Name();
			userPass = opConfig.getUser1Pass();
			TestStepResult result = runUserAuth(type);
			if (result != TestStepResult.PASS) {
				return result;
			}
			logger.log(String.format("Authentication of User %s with password %s completed", userName, userPass));
			// now do the same for the second user

			loadDriver(true); // will this clear all sessions in chrome-headless? it does for phantomjs...

			userName = opConfig.getUser2Name();
			userPass = opConfig.getUser2Pass();
			result = runUserAuth(RPType.HONEST);
			if (result != TestStepResult.PASS) {
				return result;
			}
			loadDriver(true);
		}
//		TestStepResult rpResult = (TestStepResult) stepCtx.get(RPContextConstants.RP_INDICATED_STEP_RESULT);
//		// return max(rpResult, result), where PASS < NOT_RUN < UNDETERMINED < FAIL
//		result = rpResult.compareTo(result) >= 0 ? rpResult : result;
//        return result;
//		return getCombinedStepResult(result);
    	return TestStepResult.PASS;
    }



    private TestStepResult runUserAuth(RPType rpType) throws InterruptedException {
		TestStepResult result = TestStepResult.NOT_RUN;
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
		logger.log(String.format("Opening browser with URL '%s'.", authnReq.toURI().toString()));
		driver.get(authnReq.toURI().toString());

		// prepare scripts for login and consent page
		evalScriptTemplates();

		logger.log(String.format("Using Login script:\n %s", submitScript));
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
		waitMillis(2000);

		// don't run consentScript if we have already been redirected back to RP
		if (!driver.getCurrentUrl().startsWith(authnReq.getRedirectionURI().toString())) {
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
//		try {
//			waitForRP.get(5, TimeUnit.SECONDS);
//		} catch (ExecutionException | TimeoutException e) {
//			logger.log("No RP confirmation about test step progress received in Browser", e);
////			return TestStepResult.UNDETERMINED;
//		}
//		logger.log("lock released");

		// take a screenshot again to show the finished site
		logger.log("Last URL seen in Browser: " + finalUrl);
		logScreenshot();


		try {
			return blockAndResult.get(10,TimeUnit.SECONDS);
		} catch (ExecutionException | TimeoutException e) {
			logger.log("Browser Timeout while waiting for RP", e);
			return TestStepResult.UNDETERMINED;
		}
	}

    private void evalScriptTemplates() {
		// todo maybe this should be part of the browser initializaiton?
		if (!Strings.isNullOrEmpty(opConfig.getLoginScript())) {
			// Todo: check for input field name and run template engine
			submitScript = opConfig.getLoginScript();
			opConfig.setLoginScript(submitScript);

		} else {
			try {
				// load default script, variable input not yet implemented
				String templateString = IOUtils.toString(getClass().getResourceAsStream("/login-form.st"), "UTF-8");
				ST st = new ST(templateString, '§', '§');
				st.add("current_user_username", userName);
				st.add("current_user_password", userPass);
				submitScript = st.render();
			} catch (IOException e) {
				throw new RuntimeException( new InterruptedException(e.getMessage()));
			}
		}

		if (!Strings.isNullOrEmpty(opConfig.getConsentScript())) {
			consentScript = opConfig.getConsentScript();
			opConfig.setConsentScript(consentScript);
		} else {
			try {
				// TODO: currently using consent script as is, not a template
				consentScript = IOUtils.toString(getClass().getResourceAsStream("/consent-form.st"), "UTF-8");
			} catch (IOException e) {
				throw new RuntimeException( new InterruptedException(e.getMessage()));
			}
		}
    }


    private AuthenticationRequest getAuthnReq(RPType rpType) {
		URI authnReq;
		authnReq = RPType.HONEST.equals(rpType)
				? (URI) stepCtx.get(RPContextConstants.RP1_PREPARED_AUTHNREQ)
					: (URI) stepCtx.get(RPContextConstants.RP2_PREPARED_AUTHNREQ);
		try {
			AuthenticationRequest req = AuthenticationRequest.parse(authnReq);
			return req;
		} catch (ParseException e) {
			logger.log("Error parsing generated AuthenticationRequest URI", e);
			return null;
		}
	}


}
