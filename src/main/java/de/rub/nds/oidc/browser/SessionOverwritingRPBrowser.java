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

package de.rub.nds.oidc.browser;

import de.rub.nds.oidc.server.op.OPContextConstants;
import de.rub.nds.oidc.server.op.OPParameterConstants;
import de.rub.nds.oidc.test_model.TestStepResult;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class SessionOverwritingRPBrowser extends DefaultRPTestBrowser {

	@Override
	public TestStepResult run() {
		try {
			// prepare futures
			CompletableFuture<TestStepResult> blockAndResult = new CompletableFuture<>();
			stepCtx.put(OPContextConstants.BLOCK_BROWSER_AND_TEST_RESULT, blockAndResult);
			CompletableFuture<?> blockSecondBrowser = new CompletableFuture<>();
			stepCtx.put(OPContextConstants.BLOCK_BROWSER_WAITING_FOR_HONEST, blockSecondBrowser);

			// determine order of AuthReq from TestPlan parameters
			String honestOpUrl = (String) stepCtx.get(OPParameterConstants.BROWSER_INPUT_HONEST_OP_URL);
			String evilOpUrl = (String) stepCtx.get(OPParameterConstants.BROWSER_INPUT_EVIL_OP_URL);
			String firstOpUrl = (String) stepCtx.get(OPParameterConstants.BROWSER_INPUT_OP_URL);
			String secondOpUrl =  firstOpUrl.equals(honestOpUrl) ? evilOpUrl : honestOpUrl;

			// open clients login page
			String startUrl = rpConfig.getUrlClientTarget();
			logger.logCodeBlock("Opening browser with URL :", startUrl);

			waitForDocumentReadyAndJsReady1(() -> {
				driver1.get(startUrl);
				return null;
			});
//			waitMillis(1000);

			// store session cookies
			Set<Cookie> cookies = driver1.manage().getCookies();

			// execute JS to start authentication
			stepCtx.put(OPParameterConstants.BROWSER_INPUT_OP_URL, firstOpUrl);
			String submitScriptRaw = rpConfig.getSeleniumScript();
			String submitScript = te.eval(createRPContext(), submitScriptRaw);

			// setup background task
			CompletableFuture.runAsync(()->{
				try {
					// wait, till first OP received authrequest
					blockSecondBrowser.get(15, TimeUnit.SECONDS);

					RemoteWebDriver second = driver2;

					// copy all cookies that were set in first browser instance
					// 1. navigate to correct domain
					second.get(startUrl);
					// 2. purge cookie jar
					second.manage().deleteAllCookies();
					// 3. set cookies from other browser instance
					cookies.stream().forEach(c -> {
//						logger.log("Copying cookie: " + c.toString());
						second.manage().addCookie(c);
					});

					// request authentication with second OP
					stepCtx.put(OPParameterConstants.BROWSER_INPUT_OP_URL, secondOpUrl);
					String submitScriptEvil = te.eval(createRPContext(), submitScriptRaw);
					logger.log("Browser starts authentication at second OP.");
					second.executeScript(submitScriptEvil);
					waitMillis(2000);
					second.quit();
				} catch (InterruptedException | TimeoutException | ExecutionException e) {
					logger.log("Failed waiting for signal to start 2nd AuthnReq", e);
				}
			});

			// start first authentication in "foreground" thread
			logger.log("Browser starts authentication at first OP.");

			waitForDocumentReadyAndJsReady1(() -> {
				driver1.executeScript(submitScript);
				return null;}
				);
//            waitMillis(1000);
//            logScreenshot();

			// wait for result of the test
			TestStepResult result = blockAndResult.get(15, TimeUnit.SECONDS);
			logger.log("Authentication result:");
			logScreenshot1();
			logger.logCodeBlock("Final URL as seen in Browser: ", driver1.getCurrentUrl());
			return result;
		} catch (TimeoutException ex) {
			logger.log("Timeout while waiting for token request, assuming test passed.");
			logScreenshot1();
			logger.logCodeBlock("Final URL as seen in Browser: ", driver1.getCurrentUrl());
			return TestStepResult.PASS;
		} catch (ExecutionException  ex) {
			logger.log("Waiting for Honest OP or test result gave an error.", ex);
			logScreenshot1();
			logger.logCodeBlock("Final URL as seen in Browser: ", driver1.getCurrentUrl());
			return TestStepResult.UNDETERMINED;
		} catch (InterruptedException ex) {
			throw new RuntimeException("Test interrupted.", ex);
		}
	}
}
