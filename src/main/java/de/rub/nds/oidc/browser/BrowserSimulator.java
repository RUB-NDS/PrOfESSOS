/****************************************************************************
 * Copyright 2016-2019 Ruhr-Universität Bochum.
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

import com.typesafe.config.Config;
import de.rub.nds.oidc.learn.TemplateEngine;
import de.rub.nds.oidc.log.TestStepLogger;
import de.rub.nds.oidc.server.op.OPParameterConstants;
import de.rub.nds.oidc.test_model.*;
import de.rub.nds.oidc.utils.Func;
import de.rub.nds.oidc.utils.InstanceParameters;
import de.rub.nds.oidc.utils.JsWaiter;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.support.ui.Sleeper;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * @author Tobias Wich
 */
public abstract class BrowserSimulator {

	protected Config seleniumCfg;

	protected RemoteWebDriver driver1;
	protected RemoteWebDriver driver2;

	protected final long NORMAL_WAIT_TIMEOUT = 15;
	protected final long MEDIUM_WAIT_TIMEOUT = 10;
	protected final long SHORT_WAIT_TIMEOUT = 5;
	protected final long SEARCH_WAIT_TIMEOUT = 1;

	protected TestRPConfigType rpConfig;
	protected TestOPConfigType opConfig;
	protected TemplateEngine te;

	protected TestStepLogger logger;
	protected Map<String, Object> suiteCtx;
	protected Map<String, Object> stepCtx;
	protected InstanceParameters params;

	private String formSubmitDelayScript;

	private JsWaiter jsWaiter1;
	private JsWaiter jsWaiter2;


	public void init(Config seleniumCfg) {
		this.seleniumCfg = seleniumCfg;
		loadDriver(false);
		loadFormSubmissionDelayScript();
	}

	protected final void reloadDriver() {
		loadDriver(true);
	}

	private void loadDriver(boolean quit) {
		if (quit) {
			quit();
		}
		driver1 = getDriverInstance();
		jsWaiter1 = new JsWaiter();
		jsWaiter1.setJsWaitDriver(driver1, MEDIUM_WAIT_TIMEOUT);

		driver2 = getDriverInstance();
		jsWaiter2 = new JsWaiter();
		jsWaiter2.setJsWaitDriver(driver2, MEDIUM_WAIT_TIMEOUT);
	}

	protected RemoteWebDriver getDriverInstance() {
		ChromeOptions chromeOptions = new ChromeOptions();

		// load chromedriver config
		System.setProperty("webdriver.chrome.driver", seleniumCfg.getString("chromedriver_path"));
		if (seleniumCfg.hasPath("chromedriver_logfile")) {
			System.setProperty("webdriver.chrome.logfile", seleniumCfg.getString("chromedriver_logfile"));
			System.setProperty("webdriver.chrome.verboseLogging", "true");
		}
		if (seleniumCfg.hasPath("chrome_browser_path")) {
			// do not search for chrome in OS $PATH
			chromeOptions.setBinary(seleniumCfg.getString("chrome_browser_path"));
		}

		chromeOptions.addArguments("incognito", "headless", "no-sandbox", "disable-gpu", "window-size=1024x768");
		// disable certificate validation to prevent chrome from getting stuck on cert errors
		// requires chrome >= 65 to work (ignored otherwise)
		chromeOptions.setAcceptInsecureCerts(true);

		RemoteWebDriver d = new ChromeDriver(chromeOptions);

		d.manage().timeouts().implicitlyWait(NORMAL_WAIT_TIMEOUT, TimeUnit.SECONDS);
		return d;
	}

	public void setTestConfig(TestConfigType config) {
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
		teCtx.put("params", params.getParamMap());

		return teCtx;
	}

	public abstract TestStepResult run() throws InterruptedException;

	public void quit() {
		if (driver1 != null) {
			driver1.quit();
		}
		if (driver2 != null) {
			driver2.quit();
		}
	}

	// This does not work in SPA scenarios where only the <html> element's content is changed
	protected final <T> T waitForPageLoad1(Func<T> func, long timeout) {
		return waitForPageLoad(func, timeout, driver1);
	}
	protected final <T> T waitForPageLoad2(Func<T> func, long timeout) {
		return waitForPageLoad(func, timeout, driver2);
	}

	private <T> T waitForPageLoad(Func<T> func, long timeout, RemoteWebDriver driver) {
		RemoteWebElement oldHtml = (RemoteWebElement) driver.findElement(By.tagName("html"));

		T result = func.call();
		WebDriverWait wait = new WebDriverWait(driver, timeout);
		wait.until((WebDriver input) -> {
			RemoteWebElement newHtml = (RemoteWebElement) driver.findElement(By.tagName("html"));
			return !newHtml.getId().equals(oldHtml.getId());
		});

		return result;
	}

	private <T> T waitForPageLoadJs(Func<T> func, long timeout, RemoteWebDriver driver, JsWaiter jsWaiter) {
		RemoteWebElement oldHtml = (RemoteWebElement) driver.findElement(By.tagName("html"));
		// Wait for old page has loaded java scripts, before try to click a button which may be created by JS
		jsWaiter.waitAllRequest();

		T result = func.call();
		WebDriverWait wait = new WebDriverWait(driver, timeout);
		wait.until((WebDriver input) -> {
			RemoteWebElement newHtml = (RemoteWebElement) driver.findElement(By.tagName("html"));
			return !newHtml.getId().equals(oldHtml.getId());
		});

		return result;
	}

	protected final <T> T waitForPageLoad1(Func<T> func) {
		return waitForPageLoad(func, MEDIUM_WAIT_TIMEOUT, driver1);
	}
	protected final <T> T waitForPageLoad2(Func<T> func) {
		return waitForPageLoad(func, MEDIUM_WAIT_TIMEOUT, driver2);
	}

	protected final <T> T waitForDocumentReadyAndJsReady1(Func<T> func) {
		return waitForDocumentReadyAndJsReady(func, driver1, jsWaiter1);
	}
	protected final <T> T waitForDocumentReadyAndJsReady2(Func<T> func) {
		return waitForDocumentReadyAndJsReady(func, driver2, jsWaiter2);
	}

	private <T> T waitForDocumentReadyAndJsReady(Func<T> func, RemoteWebDriver driver, JsWaiter jsWaiter) {
		// first, try if a new <html> element can be detected within 3 seconds
		try {
			T result = waitForPageLoadJs(func, 3, driver, jsWaiter);
		} catch (org.openqa.selenium.TimeoutException e) {
			// ignore
//			logger.log("debug: timeout during waitForPageLoad");
		}
		// next, even if no new html element was detected, wait until
		// document.readyState is complete and check various JS frameworks if
		// they finished loading and are ready. Currently, jsWaiter waits for up to 10 seconds
		jsWaiter.waitAllRequest();
		return null;
	}

	protected void waitForDocumentReady1() {
		waitForDocumentReady(driver1);
	}
	protected void waitForDocumentReady2() {
		waitForDocumentReady(driver2);
	}

	protected void waitForDocumentReady(RemoteWebDriver driver) {
		WebDriverWait wait = new WebDriverWait(driver, MEDIUM_WAIT_TIMEOUT);
		wait.until((WebDriver d) -> driver.executeScript("return document.readyState").equals("complete"));
	}

	protected void waitMillis(long timeout) throws InterruptedException {
		Sleeper.SYSTEM_SLEEPER.sleep(Duration.ofMillis(timeout));
	}

	protected void logScreenshot1() {
		logScreenshot(driver1);
	}
	protected void logScreenshot2() {
		logScreenshot(driver2);
	}

	protected void logScreenshot(RemoteWebDriver driver) {
		byte[] screenshot = driver.getScreenshotAs(OutputType.BYTES);
		logger.log(screenshot, "image/png");
	}

	protected <T> T withSearchTimeout1(Func<T> fun) {
		return withSearchTimeout(fun, driver1);
	}
	protected <T> T withSearchTimeout2(Func<T> fun) {
		return withSearchTimeout(fun, driver2);
	}

	protected <T> T withSearchTimeout(Func<T> fun, RemoteWebDriver driver) {
		try {
			driver.manage().timeouts().implicitlyWait(SEARCH_WAIT_TIMEOUT, TimeUnit.SECONDS);
			return fun.call();
		} finally {
			driver.manage().timeouts().implicitlyWait(MEDIUM_WAIT_TIMEOUT, TimeUnit.SECONDS);
		}
	}

	protected String getFormSubmitDelayScript() {
		return formSubmitDelayScript;
	}

	private void loadFormSubmissionDelayScript() {
		try {
			String script = new String(getClass().getResourceAsStream("/delayFormSubmission.js").readAllBytes(), "UTF-8");
			formSubmitDelayScript = script;
		} catch (IOException e) {
			logger.log("Failed to load script template from resources.", e);
		}
	}

}
