package de.rub.nds.oidc.browser.op;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.rp.OIDCClientInformation;
import de.rub.nds.oidc.browser.BrowserSimulator;
import de.rub.nds.oidc.server.rp.RPContextConstants;
import de.rub.nds.oidc.server.rp.RPParameterConstants;
import de.rub.nds.oidc.server.rp.RPType;
import de.rub.nds.oidc.test_model.TestStepResult;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nonnull;
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
	protected String finalUrl;
	protected CompletableFuture<TestStepResult> blockAndResult;
	protected CompletableFuture<?> blockRP;

	protected void initRPLocks() {
		// prepare locks and share with RP
		blockRP = new CompletableFuture();
		stepCtx.put(RPContextConstants.BLOCK_RP_FOR_BROWSER_FUTURE, blockRP);

		blockAndResult = new CompletableFuture<TestStepResult>();
		stepCtx.put(RPContextConstants.BLOCK_BROWSER_AND_TEST_RESULT, blockAndResult);
	}

	protected void evalScriptTemplates() {
		String templateString;

		try {
			if (!Strings.isNullOrEmpty(opConfig.getLoginScript())) {
				templateString = opConfig.getLoginScript();
			} else {
				templateString = IOUtils.toString(getClass().getResourceAsStream("/login-form.st"), "UTF-8");
			}

			opConfig.setLoginScript(templateString);
			Map<String, String> user = ImmutableMap.of("current_user_username", userName, "current_user_password", userPass);
			submitScript = te.eval(Maps.newHashMap(user), templateString);

			if (!Strings.isNullOrEmpty(opConfig.getConsentScript())) {
				consentScript = opConfig.getConsentScript();
			} else {
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
			return authnReq.toString();
		} else {
			throw new ClassCastException("Stored Authentication Request object is neither String nor URI");
		}
	}

	protected RPType getStartRpType() {
		String starter = (String) stepCtx.getOrDefault(RPContextConstants.START_RP_TYPE, "HONEST");
		RPType startType = starter.equals(RPType.HONEST.toString()) ? RPType.HONEST : RPType.EVIL;
		return startType;
	}

	protected String getClientName(RPType type) {
		OIDCClientInformation ci;
		if (type.equals(RPType.HONEST)) {
			ci = (OIDCClientInformation) suiteCtx.get(RPContextConstants.HONEST_CLIENT_CLIENTINFO);
		} else {
			ci = (OIDCClientInformation) suiteCtx.get(RPContextConstants.EVIL_CLIENT_CLIENTINFO);
		}
		String name = ci.getMetadata().getName();
		if (name == null) {
			name = "Unknown Client Name";
		}
		return name;
	}

	protected TestStepResult runUserAuth(RPType rpType) throws InterruptedException {
		logger.log(String.format("Start user authentication for %s with username: %s", getClientName(rpType), userName));

		// store user credentials to make them accessible to RP
		stepCtx.put(RPContextConstants.CURRENT_USER_USERNAME, userName);
		stepCtx.put(RPContextConstants.CURRENT_USER_PASSWORD, userPass);
		initRPLocks();

		// load configured values

		// start authentication
		String authnReq = getAuthnReqString(rpType);
		logger.logCodeBlock("Authentication Request URL:", authnReq);
		driver.get(authnReq);

		// delay form submissions to allow capturing screenshots
		driver.executeScript(getFormSubmitDelayScript());
		waitMillis(500);

		// prepare scripts for login and consent page
		evalScriptTemplates();
//		logger.log(String.format("Using Login script:%n %s", submitScript));

		try {
			waitForDocumentReadyAndJsReady(() -> {
				driver.executeScript(submitScript);
				// capture state where the text is entered
				//			logScreenshot();
				logger.log("Login Credentials entered");
				return null;
			});

			// do not run consentScript if we have already been redirected back to RP
			String location = driver.getCurrentUrl();
			if (consentRequired(location)) {
				driver.executeScript(getFormSubmitDelayScript());
				logger.log("Running Consent-Script to authorize the client");

				waitForPageLoad(() -> {
					driver.executeScript(consentScript);
//					logScreenshot();
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
		}

		confirmBrowserFinished();
		return waitForRPResult();
	}

	protected void confirmBrowserFinished() {
		// store URL to make sure RP can access URI fragments (implicit/hybrid)
		finalUrl = driver.getCurrentUrl();
		stepCtx.put(RPContextConstants.LAST_BROWSER_URL, finalUrl);
		logger.logCodeBlock("Final URL as seen in Browser:", finalUrl);
		// confirm submission of redirect uri
		blockRP.complete(null);
	}

	protected TestStepResult waitForRPResult(@Nonnull int timeout) throws InterruptedException {
		try {
			// wait for TestStepResult from RP
			return blockAndResult.get(timeout, TimeUnit.SECONDS);
		} catch (ExecutionException | TimeoutException e) {

			logger.log("Browser Timeout while waiting for RP");
//			logScreenshot();
			logger.log("Authentication failed, assuming test passed.");
			return TestStepResult.PASS;
		}
	}

	protected TestStepResult waitForRPResult() throws InterruptedException {
		// set default timeout to 10 seconds
		int timeout = 10;
		return waitForRPResult(timeout);
	}

	protected boolean consentRequired(String browserUrl) {
		URI honestRedirect = (URI) stepCtx.get(RPContextConstants.RP1_PREPARED_REDIRECT_URI);
		URI evilRedirect = (URI) stepCtx.get(RPContextConstants.RP2_PREPARED_REDIRECT_URI);

		if (browserUrl.startsWith(honestRedirect.toString()) || browserUrl.startsWith(evilRedirect.toString())) {
			logger.log("No consent page encountered in browser");
			return false;
		}
		return true;
	}
}
