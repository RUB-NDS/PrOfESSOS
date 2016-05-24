/****************************************************************************
 * Copyright 2016 Ruhr-Universität Bochum.
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author Tobias Wich
 */
public class IdPConfusionBrowser extends DefaultRPTestBrowser {

	@Override
	public TestStepResult run() {
		try {
			// prepare futures
			CompletableFuture<?> blockUntilHonest = new CompletableFuture<>();
			stepCtx.put(OPContextConstants.BLOCK_BROWSER_WAITING_FOR_HONEST, blockUntilHonest);
			CompletableFuture<TestStepResult> blockAndResult = new CompletableFuture<>();
			stepCtx.put(OPContextConstants.BLOCK_BROWSER_AND_TEST_RESULT, blockAndResult);

			// open clients login page
			String startUrl = rpConfig.getUrlClientTarget();
			logger.log(String.format("Opening browser with URL '%s'.", startUrl));
			driver.get(startUrl);

			// execute JS to start authentication
			String honestInputOpUrl = (String) stepCtx.get(OPParameterConstants.BROWSER_INPUT_HONEST_OP_URL);
			stepCtx.put(OPParameterConstants.BROWSER_INPUT_OP_URL, honestInputOpUrl);
			String submitScriptRaw = rpConfig.getSeleniumScript();
			String submitScript = te.eval(createRPContext(), submitScriptRaw);
			logger.log("Start authentication for Honest OP.");
			driver.executeScript(submitScript);
			// wait for honest op to answer
			blockUntilHonest.get(60, TimeUnit.SECONDS);

			// restore default test conditions
			String evilInputOpUrl = (String) stepCtx.get(OPParameterConstants.BROWSER_INPUT_EVIL_OP_URL);
			stepCtx.put(OPParameterConstants.BROWSER_INPUT_OP_URL, evilInputOpUrl);

			// now request evil op
			logger.log(String.format("Opening browser with URL '%s'.", startUrl));
			driver.get(startUrl);

			// execute JS to start authentication
			submitScriptRaw = rpConfig.getSeleniumScript();
			submitScript = te.eval(createRPContext(), submitScriptRaw);
			logger.log("Start authentication for Evil OP.");
			driver.executeScript(submitScript);
			// wait for result of the test
			return blockAndResult.get(60, TimeUnit.SECONDS);
		} catch (ExecutionException | TimeoutException ex) {
			logger.log("Waiting for Honest OP  or test result gave an error.", ex);
			logScreenshot();
			return TestStepResult.UNDETERMINED;
		} catch (InterruptedException ex) {
			throw new RuntimeException("Test interrupted.", ex);
		}
	}
	
}
