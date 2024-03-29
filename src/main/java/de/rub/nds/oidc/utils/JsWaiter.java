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

package de.rub.nds.oidc.utils;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;


/**
 * This class is an adaption of Onur Baskirt's JSWaiter which
 * has been published free to use without a License at
 * https://web.archive.org/web/20190316095049/https://www.swtestacademy.com/selenium-wait-javascript-angular-ajax/
 */
public class JsWaiter {

	private RemoteWebDriver jsWaitDriver;
	private WebDriverWait jsWait;
	private JavascriptExecutor jsExec;

	private static void poll(long milis) {
		try {
			Thread.sleep(milis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	// set the driver 
	public void setJsWaitDriver(RemoteWebDriver driver, long timeout) {
		this.jsWaitDriver = driver;
		this.jsWait = new WebDriverWait(jsWaitDriver, timeout);
		this.jsExec = jsWaitDriver;
	}

	private void ajaxComplete() {
		jsExec.executeScript("var callback = arguments[arguments.length - 1];"
				+ "var xhr = new XMLHttpRequest();" + "xhr.open('GET', '/Ajax_call', true);"
				+ "xhr.onreadystatechange = function() {" + "  if (xhr.readyState == 4) {"
				+ "    callback(xhr.responseText);" + "  }" + "};" + "xhr.send();");
	}

	private void waitForJQueryLoad() {
		try {
//			ExpectedCondition<Boolean> jQueryLoad = driver -> ((Long) ((JavascriptExecutor) jsWaitDriver)
//					.executeScript("return jQuery.active") == 0);
			ExpectedCondition<Boolean> jQueryLoad = driver -> (((String) ((JavascriptExecutor) jsWaitDriver)
					.executeScript("return window.jQuery && window.jQuery.active == 0")).equals("true"));

			boolean jqueryReady = (Boolean) jsExec.executeScript("return jQuery.active==0");

			if (!jqueryReady) {
				jsWait.until(jQueryLoad);
			}
		} catch (WebDriverException ignored) {
		}
	}

	private void waitForAngularLoad() {
		String angularReadyScript = "return angular.element(document).injector().get('$http').pendingRequests.length === 0";
		angularLoads(angularReadyScript);
	}

	private void waitUntilJSReady() {
		try {
			ExpectedCondition<Boolean> jsLoad = driver -> ((JavascriptExecutor) jsWaitDriver)
					.executeScript("return document.readyState").toString().equals("complete");

			boolean jsReady = jsExec.executeScript("return document.readyState").toString().equals("complete");

			if (!jsReady) {
				jsWait.until(jsLoad);
			}
		} catch (WebDriverException ignored) {
		}
	}

	private void waitUntilJQueryReady() {
		Boolean jQueryDefined = (Boolean) jsExec.executeScript("return typeof jQuery != 'undefined'");
		if (jQueryDefined) {
			poll(20);

			waitForJQueryLoad();

			poll(20);
		}
	}

	public void waitUntilAngularReady() {
		try {
			Boolean angularUnDefined = (Boolean) jsExec.executeScript("return window.angular === undefined");
			if (!angularUnDefined) {
				Boolean angularInjectorUnDefined = (Boolean) jsExec.executeScript("return angular.element(document).injector() === undefined");
				if (!angularInjectorUnDefined) {
					poll(20);

					waitForAngularLoad();

					poll(20);
				}
			}
		} catch (WebDriverException ignored) {
		}
	}

	public void waitUntilAngular5Ready() {
		try {
			Object angular5Check = jsExec.executeScript("return getAllAngularRootElements()[0].attributes['ng-version']");
			if (angular5Check != null) {
				Boolean angularPageLoaded = (Boolean) jsExec.executeScript("return window.getAllAngularTestabilities().findIndex(x=>!x.isStable()) === -1");
				if (!angularPageLoaded) {
					poll(20);

					waitForAngular5Load();

					poll(20);
				}
			}
		} catch (WebDriverException ignored) {
		}
	}

	private void waitForAngular5Load() {
		String angularReadyScript = "return window.getAllAngularTestabilities().findIndex(x=>!x.isStable()) === -1";
		angularLoads(angularReadyScript);
	}

	private void angularLoads(String angularReadyScript) {
		try {
			ExpectedCondition<Boolean> angularLoad = driver -> Boolean.valueOf(((JavascriptExecutor) driver)
					.executeScript(angularReadyScript).toString());

			boolean angularReady = Boolean.valueOf(jsExec.executeScript(angularReadyScript).toString());

			if (!angularReady) {
				jsWait.until(angularLoad);
			}
		} catch (WebDriverException ignored) {
		}
	}

	public void waitAllRequest() {
		// FIXME Add time for lazy loaded elements, not nice but generic.
		//  waitForElementAreComplete() would maybe work, but Element is different per webapp
		poll(400);
		waitUntilJSReady();
//		ajaxComplete();
		waitUntilJQueryReady();
		waitUntilAngularReady();
		waitUntilAngular5Ready();
	}

	/**
	 * Method to make sure a specific element has loaded on the page
	 *
	 * @param by
	 * @param expected
	 */
	public void waitForElementAreComplete(By by, int expected) {
		ExpectedCondition<Boolean> angularLoad = driver -> {
			int loadingElements = jsWaitDriver.findElements(by).size();
			return loadingElements >= expected;
		};
		jsWait.until(angularLoad);
	}

	/**
	 * Waits for the elements animation to be completed
	 *
	 * @param css
	 */
	public void waitForAnimationToComplete(String css) {
		ExpectedCondition<Boolean> angularLoad = driver -> {
			int loadingElements = jsWaitDriver.findElements(By.cssSelector(css)).size();
			return loadingElements == 0;
		};
		jsWait.until(angularLoad);
	}
}
