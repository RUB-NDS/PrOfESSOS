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

public class SessionOverwritingOPBrowser extends DefaultRPTestBrowser {

    @Override
    public TestStepResult run() {
        try {
            // prepare future
            CompletableFuture<TestStepResult> blockAndResult = new CompletableFuture<>();
            stepCtx.put(OPContextConstants.BLOCK_BROWSER_AND_TEST_RESULT, blockAndResult);
            
            // determine order of AuthReq from TestPlan parameters
            String honestOpUrl = (String) stepCtx.get(OPParameterConstants.BROWSER_INPUT_HONEST_OP_URL);
			String evilOpUrl = (String) stepCtx.get(OPParameterConstants.BROWSER_INPUT_EVIL_OP_URL);

			String firstOpUrl = (String) stepCtx.get(OPParameterConstants.BROWSER_INPUT_OP_URL);
			String secondOpUrl =  firstOpUrl.equals(honestOpUrl) ? evilOpUrl : honestOpUrl;

            // open clients login page
            String startUrl = rpConfig.getUrlClientTarget();
            logger.log(String.format("Opening browser with URL '%s'.", startUrl));
            driver.get(startUrl);
			waitMillis(1000);  // driver.get() should already block and only release once document.readyState is complete

			// store session cookies
			Set<Cookie> cookies = driver.manage().getCookies();

            // execute JS to start authentication
            stepCtx.put(OPParameterConstants.BROWSER_INPUT_OP_URL, firstOpUrl);
            String submitScriptRaw = rpConfig.getSeleniumScript();
            String submitScript = te.eval(createRPContext(), submitScriptRaw);
            logger.log("Browser starts authentication at first OP.");
            driver.executeScript(submitScript);
            waitMillis(1000);

			RemoteWebDriver second = getDriverInstance();
			
			// copy all cookies that were set in first browser instance
			// 1. navigate to correct domain
			second.get(startUrl);
			// 2. purge cookie jar
			second.manage().deleteAllCookies();
			// 3. set cookies from other browser instance
			Iterator<Cookie> it = cookies.iterator();
			while (it.hasNext()) {
				Cookie c = it.next();
//				logger.log("Cookie: " + c.toString());
				second.manage().addCookie(c);
			}

            // ... and request authentication with Evil OP
            stepCtx.put(OPParameterConstants.BROWSER_INPUT_OP_URL, secondOpUrl);
            String submitScriptEvil = te.eval(createRPContext(), submitScriptRaw);
            logger.log("Browser starts authentication at second OP.");
            second.executeScript(submitScriptEvil);

            // wait for result of the test
            TestStepResult result = blockAndResult.get(10, TimeUnit.SECONDS);
            logger.log("Authentication result:");
			logScreenshot();
			return result;
        } catch (TimeoutException ex) {
            logger.log("Timeout while waiting for token request, assuming test passed.");
            logScreenshot();
            return TestStepResult.PASS;
        } catch (ExecutionException  ex) {
            logger.log("Waiting for Honest OP or test result gave an error.", ex);
            logScreenshot();
            return TestStepResult.UNDETERMINED;
        } catch (InterruptedException ex) {
            throw new RuntimeException("Test interrupted.", ex);
        } // TODO: close second browser in finally
    }
}
