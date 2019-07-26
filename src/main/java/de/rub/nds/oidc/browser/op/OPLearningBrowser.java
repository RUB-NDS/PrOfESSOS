/****************************************************************************
 * Copyright 2019 Ruhr-Universität Bochum.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ***************************************************************************/

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
		driver1.get(authnReq);
		// delay form submissions to allow for taking screenshots
		// using selenium (after executeScript() returned)
		driver1.executeScript(getFormSubmitDelayScript());
		waitMillis(500);

		// prepare scripts for login and consent page
		evalScriptTemplates();
		try {
			logger.logCodeBlock("Using Login script:", submitScript);

			waitForDocumentReadyAndJsReady1(() -> {
				driver1.executeScript(submitScript);
				// capture state where the text is entered
				logger.log("Login Credentials entered");
				logScreenshot1();
				return null;
			});
		} catch (Exception e) {
			logger.log("Execution of login script failed", e);
			logScreenshot1();

			return TestStepResult.UNDETERMINED;
		}
		logger.log("HTML element found in Browser.");

		// don't run consentScript if we have already been redirected back to RP
		String location = driver1.getCurrentUrl();
		if (location.startsWith(honestRedirect.toString()) || location.startsWith(evilRedirect.toString())) {
			logger.log("No consent page encountered in browser");
		} else {
			try {
				driver1.executeScript(getFormSubmitDelayScript());
				logger.logCodeBlock("Using Consent script:", consentScript);

				waitForPageLoad1(() -> {
					driver1.executeScript(consentScript);
					logScreenshot1();
					logger.log("ConsentScript executed, client authorized.");
					return null;
				});
			} catch (Exception e) {
				logger.log("Execution of consent script failed.", e);
				logScreenshot1();

				return TestStepResult.UNDETERMINED;
			}
		}
		
		confirmBrowserFinished();
		logScreenshot1();

		try {
			return blockAndResult.get(10, TimeUnit.SECONDS);
		} catch (ExecutionException | TimeoutException e) {
			logger.log("Browser Timeout while waiting for RP");
			return TestStepResult.UNDETERMINED;
		}
	}

}
