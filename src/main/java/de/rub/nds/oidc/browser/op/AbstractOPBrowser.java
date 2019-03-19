package de.rub.nds.oidc.browser.op;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import de.rub.nds.oidc.browser.BrowserSimulator;
import de.rub.nds.oidc.server.OPIVConfig;
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

	protected String getAuthnReqString(RPType rpType) {
		Object authnReq;
		authnReq = RPType.HONEST.equals(rpType)
					? stepCtx.get(RPContextConstants.RP1_PREPARED_AUTHNREQ)
					: stepCtx.get(RPContextConstants.RP2_PREPARED_AUTHNREQ);
		if (authnReq instanceof String) {
			return (String) authnReq;
		} else if (authnReq instanceof URI) {
			return ((URI) authnReq).toString();
		} else {
			throw new ClassCastException("Stored Authentication Request object is neither String nor URI");
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

		// load configured values
		String authnReq = getAuthnReqString(rpType);
		URI honestRedirect = (URI) stepCtx.get(RPContextConstants.RP1_PREPARED_REDIRECT_URI);
		URI evilRedirect = (URI) stepCtx.get(RPContextConstants.RP2_PREPARED_REDIRECT_URI);

		// start authentication
		logger.logCodeBlock(authnReq, "Authentication Request URL:");
		driver.get(authnReq);

		// delay form submissions to allow capturing screenshots
		driver.executeScript(getFormSubmitDelayScript());
		waitMillis(500);

		// prepare scripts for login and consent page
		evalScriptTemplates();
//		logger.log(String.format("Using Login script:%n %s", submitScript));

		try {
			// wait until a new html element appears, indicating a page load
			waitForDocumentReadyAndJsReady(() -> {
				driver.executeScript(submitScript);
				// capture state where the text is entered
				//			logScreenshot();
				logger.log("Login Credentials entered");
				return null;
			});
			// logger.log("HTML element found in Browser.");
			// wait a bit more in case we have an angular app or some other JS heavy application
			waitMillis(1000);

			// don not run consentScript if we have already been redirected back to RP
			String location = driver.getCurrentUrl();
			if (location.startsWith(honestRedirect.toString()) || location.startsWith(evilRedirect.toString())) {
				logger.log("No consent page encountered in browser");
			} else {
				driver.executeScript(getFormSubmitDelayScript());
				logger.log("Running Consent-Script to authorize the client");

				waitForDocumentReadyAndJsReady(() -> {
					driver.executeScript(consentScript);
					//				logScreenshot();
					return null;
				});
			}
		} catch (Exception e) {
			// script execution failed, likely received an error response earlier due to wrong redirect uri
			logger.log("Script execution failed, please check manually");
			logScreenshot();

			if (params.getBool(RPParameterConstants.SCRIPT_EXEC_EXCEPTION_FAILS_TEST)) {
				return TestStepResult.FAIL;
			}
//
//			logger.log("Authentication failed, assuming test passed.");
//			return TestStepResult.PASS;
		}

		String finalUrl = driver.getCurrentUrl();
		stepCtx.put(RPContextConstants.LAST_BROWSER_URL, finalUrl);
		logger.logCodeBlock(finalUrl, "Final URL as seen in Browser:");
		// confirm submission of redirect uri
		blockRP.complete(null);

		try {
			return blockAndResult.get(5, TimeUnit.SECONDS); // TODO: is 5 seconds long enough?
		} catch (ExecutionException | TimeoutException e) {
			// check for manipulated subdomain in Browser URI
			String uriManipulator = (String) stepCtx.get(RPContextConstants.REDIRECT_URI_MANIPULATOR);
			String strippedUrl = finalUrl.replaceFirst("^https?:\\/\\/", "");
			if (uriManipulator != null && strippedUrl.startsWith(uriManipulator)) {
				// TODO: add seleniumProxy (BrowserMob) and intercept DNS/HTTP for manipulated URIs?
				logger.log("Redirect to manipulated redirect_uri detected, assuming test failed.");
				return TestStepResult.FAIL;
			}

			logger.log("Browser Timeout while waiting for RP");
//			logScreenshot();
			logger.log("Authentication failed, assuming test passed.");
			return TestStepResult.PASS;
		}
	}


}
