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

public class KC6OPBrowser extends DefaultRPTestBrowser {

	private Set<Cookie> cookieSet;
	private String submitScriptRaw;
	private String startOpUrl;

	@Override
	public TestStepResult run() {
//        try {
		// prepare futures
		CompletableFuture<?> blockBrowser = new CompletableFuture<>();
		stepCtx.put(OPContextConstants.BLOCK_BROWSER_FUTURE, blockBrowser);
		CompletableFuture<?> opLock = new CompletableFuture<>();
		stepCtx.put(OPContextConstants.BLOCK_OP_FUTURE, opLock);

		submitScriptRaw = rpConfig.getSeleniumScript();

		// open clients login page
		String startUrl = rpConfig.getUrlClientTarget();
		startOpUrl = startUrl;
		logger.log(String.format("Opening browser with URL '%s'.", startUrl));
		driver.get(startUrl);
		Set<Cookie> cookies = driver.manage().getCookies();
		cookieSet = cookies;

		// execute JS to start authentication at Evil OP
		String evilInputOpUrl = (String) stepCtx.get(OPParameterConstants.BROWSER_INPUT_EVIL_OP_URL);
		stepCtx.put(OPParameterConstants.BROWSER_INPUT_OP_URL, evilInputOpUrl);
		String submitScriptEvil = te.eval(createRPContext(), submitScriptRaw);
		logger.log("Start authentication for Evil OP.");


		// Get a reference to blockBrowser before running
		// honest auth - otherwise the OP will block which also freezes the browser thread for
		// unknown reason...).
		// Therfore, we wait for the completion of blockBrowser in another thread
		CompletableFuture<?> waitInBackground = CompletableFuture.supplyAsync(() -> {
			// wait until authrequest has been received in Evil Op
			try {
				blockBrowser.get(15, TimeUnit.SECONDS);
				logger.log("browser release received");
				// start authentication at honest OP
				runHonestAuth();
				return null;
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				logger.log("browser blocker timedout or got interrupted");
				return null;
			}
		});

		// start authentication at Evil OP
		waitForPageLoad(() -> {
			driver.executeScript(submitScriptEvil);
			return null;
		});

		// wait, until background job is done (i.e., AuthReq  has been received at Honest
		// and AuthnResp from Evil has been released)
		waitInBackground.join();

		logger.log("Authentication result for Evil OP.");
		logScreenshot();

		// save the location of the finished state
		boolean urlReached = rpConfig.getFinalValidUrl().equals(driver.getCurrentUrl());
		boolean forceSuccessUrlFails = params.getBool(OPParameterConstants.FORCE_SUCCESS_URL_FAILS);
		if (forceSuccessUrlFails && urlReached) {
			logger.log("Target URL reached. Assuming login is successful.");
			logger.log("Successful login fails the test");
			return TestStepResult.FAIL;
		}
		if (!urlReached) {
			logger.log("Target URL not reached. Assuming login is not successful.");
			return TestStepResult.PASS;
		}

		return TestStepResult.UNDETERMINED;
	}

	private void runHonestAuth() {
		try {
			RemoteWebDriver second = getDriverInstance();
			// copy all cookies that were set in first browser instance
			// 1. navigate to correct domain
			second.get(startOpUrl);
			// 2. purge cookie jar
			second.manage().deleteAllCookies();
			// 3. set cookies from other browser instance
			cookieSet.forEach((c) -> second.manage().addCookie(c));
			// 4. request authentication with honest OP
			String honestInputOpUrl = (String) stepCtx.get(OPParameterConstants.BROWSER_INPUT_HONEST_OP_URL);
			stepCtx.put(OPParameterConstants.BROWSER_INPUT_OP_URL, honestInputOpUrl);
			String submitScript = te.eval(createRPContext(), submitScriptRaw);
			logger.log("Start authentication for Honest OP.");

			second.executeScript(submitScript);

			// wait a bit to make sure authResp from evil was received before returning
			waitMillis(2000);
			second.quit();
		} catch (InterruptedException e) {
			logger.log("Exception while running second authentication", e);
		}
	}
}
