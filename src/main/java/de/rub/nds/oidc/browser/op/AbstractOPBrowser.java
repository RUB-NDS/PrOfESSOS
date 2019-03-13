package de.rub.nds.oidc.browser.op;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import de.rub.nds.oidc.browser.BrowserSimulator;
import de.rub.nds.oidc.server.rp.RPContextConstants;
import de.rub.nds.oidc.server.rp.RPParameterConstants;
import de.rub.nds.oidc.server.rp.RPType;
import de.rub.nds.oidc.test_model.TestStepResult;
import org.apache.commons.io.IOUtils;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AbstractOPBrowser extends BrowserSimulator {

	protected String submitScript;
	protected String consentScript;
	protected String userName;
	protected String userPass;

	protected void evalScriptTemplates() {
		String templateString;

		try {
			if (!Strings.isNullOrEmpty(opConfig.getLoginScript())) {
				// Todo: check for input field name and run template engine
				templateString = opConfig.getLoginScript();
			} else {
				templateString = IOUtils.toString(getClass().getResourceAsStream("/login-form.st"), "UTF-8");
			}

			opConfig.setLoginScript(templateString); // TODO: if we add an inputfieldname, we need to evaluate this once
			Map<String, String> user = ImmutableMap.of("current_user_username", userName, "current_user_password", userPass);
			submitScript = te.eval(Maps.newHashMap(user), templateString);

			if (!Strings.isNullOrEmpty(opConfig.getConsentScript())) {
				consentScript = opConfig.getConsentScript();
			} else {
				// TODO: currently using consent script as is, not a template
				consentScript = IOUtils.toString(getClass().getResourceAsStream("/consent-form.st"), "UTF-8");
			}
			opConfig.setConsentScript(consentScript);

		} catch (IOException e) {
			logger.log("Scripttemplate evaluation failed");
			throw new RuntimeException(new InterruptedException(e.getMessage()));
		}
	}


	protected AuthenticationRequest getAuthnReq(RPType rpType) {
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


	protected TestStepResult runUserAuth(RPType rpType) throws InterruptedException {
//		TestStepResult result = TestStepResult.NOT_RUN;
//		logger.log("run userAuth");

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
		logger.logCodeBlock(authnReq.toURI().toString(), "Authentication Request URL:");
		driver.get(authnReq.toURI().toString());
		// delay form submissions for screenshots
		driver.executeScript(getFormSubmitDelayScript());
		waitMillis(500);

		// prepare scripts for login and consent page
		evalScriptTemplates();
//		logger.log(String.format("Using Login script:%n %s", submitScript));

		try {
			// wait until a new html element appears, indicating a page load
			waitForPageLoad(() -> {
				driver.executeScript(submitScript);
				// capture state where the text is entered
				//			logScreenshot();
				logger.log("Login Credentials entered");
				return null;
			});
			//		logger.log("HTML element found in Browser.");
			// wait a bit more in case we have an angular app or some other JS heavy application
			waitMillis(1000);

			// don't run consentScript if we have already been redirected back to RP
			if (driver.getCurrentUrl().startsWith(authnReq.getRedirectionURI().toString())) {
				logger.log("No consent page encountered in browser");
			} else {
				driver.executeScript(getFormSubmitDelayScript());

				waitForPageLoad(() -> {
					driver.executeScript(consentScript);
					//				logScreenshot();
					logger.log("ConsentScript executed, client authorized");
					return null;
				});
			}
		} catch (Exception e) {
			// script execution failed, likely received an error response earlier due to wrong redirect uri
			logger.log("Script execution failed, please check manually");
			logScreenshot();

			// TODO: can we always PASS the test here? in which cases should we remain UNDETERMINED?
			if (params.getBool(RPParameterConstants.SCRIPT_EXEC_EXCEPTION_FAILS_TEST)) {
				return TestStepResult.FAIL;
			}
			logger.log("Authentication failed, assuming test passed.");
			return TestStepResult.PASS;
		}


		String finalUrl = driver.getCurrentUrl();
		stepCtx.put(RPContextConstants.LAST_BROWSER_URL, finalUrl);
		logger.log("Final URL as seen in Browser:\n" + finalUrl);
		// confirm submission of redirect uri
		blockRP.complete(null);


		try {
			return blockAndResult.get(5, TimeUnit.SECONDS); // TODO: is 5 seconds long enough? 
		} catch (ExecutionException | TimeoutException e) {
			logger.log("Browser Timeout while waiting for RP");
			logScreenshot();

			// check for manipulated URI
			String uriManipulator = (String) stepCtx.get(RPContextConstants.REDIRECT_URI_MANIPULATOR);
			URI url = UriBuilder.fromUri(finalUrl).build();
			if (url != null && url.getHost().startsWith(uriManipulator)) {
				// TODO: add seleniumProxy (BrowserMob) and intercept DNS/HTTP for manipulated URIs?
				logger.log("Redirect to manipulated redirect_uri detected, assuming test failed.");
				return TestStepResult.FAIL;
			}

			return TestStepResult.UNDETERMINED;
		}
	}


}
