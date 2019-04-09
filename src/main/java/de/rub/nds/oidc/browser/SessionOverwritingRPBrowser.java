package de.rub.nds.oidc.browser;

import de.rub.nds.oidc.server.op.OPContextConstants;
import de.rub.nds.oidc.server.op.OPParameterConstants;
import de.rub.nds.oidc.test_model.TestStepResult;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.util.ArrayList;
import java.util.Iterator;
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
			logger.logCodeBlock(startUrl, "Opening browser with URL :");

			waitForPageLoad(() -> {
				driver.get(startUrl);
				return null;
			});
//			waitMillis(1000);

			// store session cookies
			Set<Cookie> cookies = driver.manage().getCookies();

			// execute JS to start authentication
			stepCtx.put(OPParameterConstants.BROWSER_INPUT_OP_URL, firstOpUrl);
			String submitScriptRaw = rpConfig.getSeleniumScript();
			String submitScript = te.eval(createRPContext(), submitScriptRaw);

			// setup background task
			CompletableFuture.runAsync(()->{
				try {
					// wait, till first OP received authrequest
					blockSecondBrowser.get(15, TimeUnit.SECONDS);

					RemoteWebDriver second = getDriverInstance();

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

			waitForPageLoad(() -> {
				driver.executeScript(submitScript);
				return null;}
				);
//            waitMillis(1000);
//            logScreenshot();

			// wait for result of the test
			TestStepResult result = blockAndResult.get(15, TimeUnit.SECONDS);
			logger.log("Authentication result:");
			logScreenshot();
			logger.logCodeBlock(driver.getCurrentUrl(), "Final URL as seen in Browser: ");
			return result;
		} catch (TimeoutException ex) {
			logger.log("Timeout while waiting for token request, assuming test passed.");
			logScreenshot();
			logger.logCodeBlock(driver.getCurrentUrl(), "Final URL as seen in Browser: ");
			return TestStepResult.PASS;
		} catch (ExecutionException  ex) {
			logger.log("Waiting for Honest OP or test result gave an error.", ex);
			logScreenshot();
			logger.logCodeBlock(driver.getCurrentUrl(), "Final URL as seen in Browser: ");
			return TestStepResult.UNDETERMINED;
		} catch (InterruptedException ex) {
			throw new RuntimeException("Test interrupted.", ex);
		}
	}
}
