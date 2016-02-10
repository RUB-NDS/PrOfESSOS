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

import de.rub.nds.oidc.test_model.TestStepResult;
import org.openqa.selenium.By;

/**
 *
 * @author Tobias Wich
 */
public class DefaultRPTestBrowser extends BrowserSimulator {

	@Override
	public TestStepResult run() {
		String startUrl = rpConfig.getUrlClientTarget();
		logger.log(String.format("Opening browser with URL '%s'.", startUrl));
		driver.get(startUrl);

		// execute JS to start authentication
		String submitScript = rpConfig.getSeleniumScript();
		submitScript = te.eval(createRPContext(), submitScript);
		//waitForPageLoad(() -> driver.executeScript(js));
		driver.executeScript(submitScript);
		// capture state where the text is entered
		logger.log("Webfinger identity entered into the login form.");

		// make sure the document is not in ready state anymore before waiting for the document ready state again
		waitMillis(100);
		waitForDocumentReady();
		// wait a bit more in case we have an angular app or some other JS heavy application
		waitMillis(400);

		// take a screenshot again to show the finished site
		logger.log("Finished login procedure, please check if it succeeded and correct the success URL and the user needle accordingly.");
		logScreenshot();

		// save the location of the finished state
		boolean urlReached = rpConfig.getFinalValidUrl().equals(driver.getCurrentUrl());
		if (! urlReached) {
			return TestStepResult.FAIL;
		}

		// TODO: see if we need to go to another URL
		if (rpConfig.getProfileUrl() != null) {
			waitMillis(100);
			waitForDocumentReady();
			// wait a bit more in case we have an angular app or some other JS heavy application
			waitMillis(400);
		}

		boolean needleFound = ! driver.findElements(By.partialLinkText(rpConfig.getUserNeedle())).isEmpty();
		return needleFound ? TestStepResult.PASS : TestStepResult.FAIL;
	}

}
