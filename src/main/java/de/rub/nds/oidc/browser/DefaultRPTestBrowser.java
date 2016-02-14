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
		String submitScriptRaw = rpConfig.getSeleniumScript();
		String submitScript = te.eval(createRPContext(), submitScriptRaw);

		// wait until a new html element appears, indicating a page load
		waitForPageLoad(() -> {
			driver.executeScript(submitScript);
			// capture state where the text is entered
			logger.log("Webfinger identity entered into the login form.");
			return null;
		});
		logger.log("HTML element found in Browser.");
		// wait a bit more in case we have an angular app or some other JS heavy application
		waitMillis(1000);

		// take a screenshot again to show the finished site
		logger.log("Finished login procedure, please check if it succeeded and correct the success URL and the user needle accordingly.");
		logScreenshot();

		// save the location of the finished state
		boolean urlReached = rpConfig.getFinalValidUrl().equals(driver.getCurrentUrl());
		if (! urlReached) {
			logger.log("Target URL not reached. Assuming login is not successful.");
			return TestStepResult.PASS;
		}

		// see if we need to go to another URL
		String profileUrl = rpConfig.getProfileUrl();
		if (profileUrl != null && ! profileUrl.isEmpty()) {
			logger.log("Loading profile URL page.");
			waitForPageLoad(() -> {
				driver.get(rpConfig.getProfileUrl());
				return null;
			});
			// wait a bit more in case we have an angular app or some other JS heavy application
			waitMillis(1000);
			logger.log("Loaded profile URL page.");
			logScreenshot();
		}

		String needle = rpConfig.getUserNeedle();
		if (needle != null && ! needle.isEmpty()) {
			needle = needle.replace("\"", "\\\""); // escape quotation marks
			String xpath = String.format("//*[contains(., \"%s\")]", needle);
			// search string
			boolean needleFound = withSearchTimeout(() -> ! driver.findElements(By.xpath(xpath)).isEmpty());

			logger.log("User needle search result: needle-found=" + needleFound);
			return needleFound ? TestStepResult.FAIL : TestStepResult.PASS;
		} else {
			logger.log("Search for user needle not possible, none specified.");
			return TestStepResult.UNDETERMINED;
		}
	}

}
