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
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.velocity.context.Context;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;


/**
 *
 * @author Tobias Wich
 */
public class RPLearningBrowser extends BrowserSimulator {

	@Override
	public final TestStepResult run() {
		String startUrl = rpConfig.getUrlClientTarget();
		logger.log(String.format("Opening browser with URL '%s'.", startUrl));
		driver.get(startUrl);

		// if we have a script skip the form detection
		if (rpConfig.getSeleniumScript() == null || rpConfig.getSeleniumScript().isEmpty()) {
			// see if someone specified a input name
			String givenInputName = rpConfig.getInputFieldName();
			if (givenInputName != null && ! givenInputName.isEmpty()) {
				String script = evalSubmitFormTemplate(givenInputName);
				logger.log("Selenium script calculated based on given input filed name.");
				logger.log(script);
				rpConfig.setSeleniumScript(script);
			} else {
				// try to detect form
				String xpath = String.format("//input[%s]", containsIgnoreCase("@name", "openid"));
				xpath += String.format("\n | //input[%s]", containsIgnoreCase("@id", "openid"));
				xpath += String.format("\n | //form[@*[%s or %s or %s]]//input[%s or %s]",
						containsIgnoreCase(".", "openid"),
						containsIgnoreCase(".", "open-id"),
						containsIgnoreCase(".", "oidc"),
						containsIgnoreCase("@name", "url"),
						containsIgnoreCase("@name", "id"));
				logger.log("Trying to find login form on the target url.");
				logger.log(xpath);

				List<WebElement> inputs = driver.findElements(By.xpath(xpath));
				// filter out duplicate elements
				inputs = inputs.stream().distinct().collect(Collectors.toList());

				// create a fill out script for each input element
				List<String> scripts = inputs.stream()
						.map(elem -> elem.getAttribute("name"))
						.map(this::evalSubmitFormTemplate)
						.collect(Collectors.toList());

				if (scripts.isEmpty()) {
					return TestStepResult.FAIL;
				} else if (scripts.size() == 1) {
					// we can continue to run the script
					logger.log("One possible input element found, trying to proceed with login process.");
					rpConfig.setInputFieldName(inputs.get(0).getAttribute("name"));
					rpConfig.setSeleniumScript(scripts.get(0));
				} else {
					logger.log("More than one possible input element found, please select the correct one and paste " +
							"the Selenium script or the field name to the respective input field and run the learning " +
							"phase again.");
					return TestStepResult.UNDETERMINED;
				}
			}
		}

		// execute JS to start authentication
		String submitScriptRaw = rpConfig.getSeleniumScript();
		String submitScript = te.eval(createRPContext(), submitScriptRaw);
		//waitForPageLoad(() -> driver.executeScript(js));

		// wait until a new html element appears, indicating a page load
		waitForPageLoad(() -> {
			driver.executeScript(submitScript);
			// capture state where the text is entered
			logger.log("Webfinger identity entered into the login form.");
			logScreenshot();
			return null;
		});
		logger.log("HTML element found in Browser.");
		// wait a bit more in case we have an angular app or some other JS heavy application
		waitMillis(1000);

		// take a screenshot again to show the finished site
		logger.log("Last URL seen in Browser: " + driver.getCurrentUrl());
		logger.log("Finished login procedure, please check if it succeeded and correct the success URL and the user needle accordingly.");
		logScreenshot();

		// save the location of the finished state
		rpConfig.setFinalValidUrl(driver.getCurrentUrl());

		return TestStepResult.PASS;
	}

	private String containsIgnoreCase(String node, String reference) {
			String upcase = "ABCDEFGHJIKLMNOPQRSTUVWXYZ";
			String lowcase = "abcdefghjiklmnopqrstuvwxyz";
		return String.format("contains(translate(%s, '%s', '%s'), '%s')", node, upcase, lowcase, reference);
	}

	private String evalSubmitFormTemplate(String inputName) {
		logger.log("Found input field with name '" + inputName + "'.");

		// eval template
		Reader r = new InputStreamReader(getClass().getResourceAsStream("/submit-form.vlt"), StandardCharsets.UTF_8);
		Context ctx = createRPContext();
		ctx.put("input-field", inputName);
		String result = te.eval(ctx, r);

		logger.log("Created Selenium script based on found input field '" + inputName + "'.");
		logger.log(result);

		return result;
	}

}
