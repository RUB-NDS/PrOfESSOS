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

import de.rub.nds.oidc.learn.TemplateEngine;
import de.rub.nds.oidc.log.TestStepLogger;
import de.rub.nds.oidc.server.op.OPParameterConstants;
import de.rub.nds.oidc.test_model.*;
import de.rub.nds.oidc.utils.Func;
import de.rub.nds.oidc.utils.InstanceParameters;
import org.apache.commons.io.IOUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.support.ui.Duration;
import org.openqa.selenium.support.ui.Sleeper;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

//import org.openqa.selenium.phantomjs.PhantomJSDriver;

/**
 * @author Tobias Wich
 */
public abstract class BrowserSimulator {

	protected RemoteWebDriver driver;

	protected long NORMAL_WAIT_TIMEOUT = 15;
	protected long SEARCH_WAIT_TIMEOUT = 1;

	protected TestRPConfigType rpConfig;
	protected TestOPConfigType opConfig;
	protected TemplateEngine te;

	protected TestStepLogger logger;
	protected Map<String, Object> suiteCtx;
	protected Map<String, Object> stepCtx;
	protected InstanceParameters params;

	private String formSubmitDelayScript;

	public BrowserSimulator() {
		loadDriver(false);
		loadFormSubmissionDelayScript();
	}

	protected final void loadDriver(boolean quit) {
		if (quit) {
			quit();
		}
		driver = getDriverInstance();
	}

	protected RemoteWebDriver getDriverInstance() {
		ChromeOptions chromeOptions = new ChromeOptions();

		// load chromedriver config
		try {
			InputStream chromeConfig = BrowserSimulator.class.getResourceAsStream("/chromedriver.properties");
			Properties p = new Properties();
			p.load(chromeConfig);

			System.setProperty("webdriver.chrome.driver", p.getProperty("chromedriver_path"));
			if (p.containsKey("chromedriver_logfile")) {
				System.setProperty("webdriver.chrome.logfile", p.getProperty("chromedriver_logfile"));
				System.setProperty("webdriver.chrome.verboseLogging", "true");
			}
			if (p.containsKey("chrome_browser_path")) {
				// do not search for chrome in OS $PATH
				chromeOptions.setBinary(p.getProperty("chrome_browser_path"));
			}
		} catch (IOException e) {
			// try default installation path
			System.setProperty("webdriver.chrome.driver", "/usr/bin/chromedriver");
		}

		chromeOptions.addArguments("headless", "no-sandbox", "disable-gpu", "window-size=1024x768",
				"disable-local-storage");
		// disable certificate validation to prevent chrome from getting stuck on cert errors
		// requires chrome >= 65 to work (ignored otherwise)
		chromeOptions.setCapability("acceptInsecureCerts", true);

		RemoteWebDriver d = new ChromeDriver(chromeOptions);


		//driver = new PhantomJSDriver();
		//driver.manage().window().setSize(new Dimension(1024, 768));
		d.manage().timeouts().implicitlyWait(NORMAL_WAIT_TIMEOUT, TimeUnit.SECONDS);
		return d;
	}

	public void setConfig(TestConfigType config) {
		if (config.getType().equals(TestRPConfigType.class.getName())) {
			setRpConfig((TestRPConfigType) config);
		} else if (config.getType().equals(TestOPConfigType.class.getName())) {
			setOpConfig((TestOPConfigType) config);
		}
	}

	public void setRpConfig(TestRPConfigType rpConfig) {
		this.rpConfig = rpConfig;
	}

	public void setOpConfig(TestOPConfigType opConfig) {
		this.opConfig = opConfig;
	}

	public void setTemplateEngine(TemplateEngine te) {
		this.te = te;
	}

	public void setLogger(TestStepLogger logger) {
		this.logger = logger;
	}

	public void setContext(Map<String, Object> suiteCtx, Map<String, Object> stepCtx) {
		this.suiteCtx = suiteCtx;
		this.stepCtx = stepCtx;
	}

	public void setParameters(List<ParameterType> params) {
		this.params = new InstanceParameters(params);
	}

	protected HashMap<String, Object> createRPContext() {
		HashMap<String, Object> ctx = createTemplateContext();
		ctx.put("rp", rpConfig);

		return ctx;
	}

	protected HashMap<String, Object> createOPContext() {
		HashMap<String, Object> ctx = createTemplateContext();
		ctx.put("op", opConfig);

		return ctx;
	}

	private HashMap<String, Object> createTemplateContext() {

		HashMap<String, Object> teCtx = new HashMap<>();

		// required for default submitScript template
		teCtx.put("browser-input-op_url", (String) stepCtx.get(OPParameterConstants.BROWSER_INPUT_OP_URL));

		// optional
		teCtx.put("suite", suiteCtx);
		teCtx.put("step", stepCtx);
		teCtx.put("params", params.getMap());

		return teCtx;
	}

	public abstract TestStepResult run() throws InterruptedException;

	public void quit() {
		driver.quit();
	}

	protected final <T> T waitForPageLoad(Func<T> func) {
		RemoteWebElement oldHtml = (RemoteWebElement) driver.findElement(By.tagName("html"));

		T result = func.call();
		WebDriverWait wait = new WebDriverWait(driver, 15);
		wait.until((WebDriver input) -> {
			RemoteWebElement newHtml = (RemoteWebElement) driver.findElement(By.tagName("html"));
			return !newHtml.getId().equals(oldHtml.getId());
		});

		return result;
	}

	protected void waitForDocumentReady() {
		WebDriverWait wait = new WebDriverWait(driver, 15);
		wait.until((WebDriver d) -> driver.executeScript("return document.readyState").equals("complete"));
	}

	protected void waitMillis(long timeout) throws InterruptedException {
		Sleeper.SYSTEM_SLEEPER.sleep(new Duration(timeout, TimeUnit.MILLISECONDS));
	}

	protected void logScreenshot() {
		byte[] screenshot = driver.getScreenshotAs(OutputType.BYTES);
		logger.log(screenshot, "image/png");
	}

	protected <T> T withSearchTimeout(Func<T> fun) {
		try {
			driver.manage().timeouts().implicitlyWait(SEARCH_WAIT_TIMEOUT, TimeUnit.SECONDS);
			return fun.call();
		} finally {
			driver.manage().timeouts().implicitlyWait(NORMAL_WAIT_TIMEOUT, TimeUnit.SECONDS);
		}
	}

//	protected TestStepResult getCombinedStepResult(TestStepResult result) {
//		TestStepResult rpResult = (TestStepResult) stepCtx.get(RPContextConstants.RP_INDICATED_STEP_RESULT);
//		// return max(rpResult, result), where PASS < NOT_RUN < UNDETERMINED < FAIL
//		result = rpResult.compareTo(result) >= 0 ? rpResult : result;
//		return result;
//	}

	protected String getFormSubmitDelayScript() {
		return formSubmitDelayScript;
	}

	private void loadFormSubmissionDelayScript() {
		try {
			String script = IOUtils.toString(getClass().getResourceAsStream("/delayFormSubmission.js"), "UTF-8");
			formSubmitDelayScript = script;
		} catch (IOException e) {
			//TODO
		}
	}

}
