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
import de.rub.nds.oidc.test_model.TestStepResult;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author Tobias Wich
 */
public class InvalidStateRPTestBrowser extends DefaultRPTestBrowser {

	@Override
	public TestStepResult run() throws InterruptedException {
		CompletableFuture<Void> blocker = new CompletableFuture<>();
		CompletableFuture<Void> reloader = new CompletableFuture<>();
		stepCtx.put(OPContextConstants.BLOCK_OP_FUTURE, blocker);
		stepCtx.put(OPContextConstants.RELOAD_BROWSER_FUTURE, reloader);

		try {
			String startUrl = rpConfig.getUrlClientTarget();
			logger.log(String.format("Opening browser with URL '%s'.", startUrl));
			driver.get(startUrl);

			// execute JS to start authentication
			String submitScriptRaw = rpConfig.getSeleniumScript();
			String submitScript = te.eval(createRPContext(), submitScriptRaw);
			driver.executeScript(submitScript);
			// capture state where the text is entered
			logger.log("Webfinger identity entered into the login form.");

			// wait until the result has been entered
			try {
				logger.log("Waiting for reload signal.");
				reloader.get(60, TimeUnit.SECONDS);
				logger.log("Reload signal received.");
			} catch (ExecutionException | TimeoutException ex) {
				logger.log("Error while waiting for reload signal.");
				return TestStepResult.UNDETERMINED;
			} catch (InterruptedException ex) {
				throw new RuntimeException("Thread interrupted while waiting for reload notification.");
			}

			// reload browser so we have a clean start
			loadDriver(true);

			// call normal processing code
			return super.run();
		} finally {
			// make sure the block is released
			blocker.complete(null);
		}
	}

}
