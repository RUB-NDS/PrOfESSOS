package de.rub.nds.oidc.browser;

import de.rub.nds.oidc.server.op.OPContextConstants;
import de.rub.nds.oidc.server.op.OPParameterConstants;
import de.rub.nds.oidc.test_model.TestStepResult;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class KC6OPBrowser extends DefaultRPTestBrowser {

    @Override
    public TestStepResult run() {
        try {
            // prepare futures
//            CompletableFuture<TestStepResult> blockBrowser = new CompletableFuture<>();
//            stepCtx.put(OPContextConstants.BLOCK_BROWSER_WAITING_FOR_HONEST, blockBrowser);

            String submitScriptRaw = rpConfig.getSeleniumScript();

            // open clients login page
            String startUrl = rpConfig.getUrlClientTarget();
            logger.log(String.format("Opening browser with URL '%s'.", startUrl));
            driver.get(startUrl);

            // execute JS to start authentication at Evil OP
            String evilInputOpUrl = (String) stepCtx.get(OPParameterConstants.BROWSER_INPUT_EVIL_OP_URL);
            stepCtx.put(OPParameterConstants.BROWSER_INPUT_OP_URL, evilInputOpUrl);
            String submitScriptEvil = te.eval(createRPContext(), submitScriptRaw);
            logger.log("Start authentication for Evil OP.");
            driver.executeScript(submitScriptEvil);

            // then open second browser window...
            String windowHandle = driver.getWindowHandle();
//            logger.log(String.format("Opening new browser window with URL '%s'.", startUrl));
            driver.executeScript("window.open('" + startUrl + "', '_blank');");

            // switch to newly openened window
            ArrayList<String> tabs = new ArrayList(driver.getWindowHandles());
//            logger.log(String.format("number of open tabs: %s", tabs.size()));
            tabs.remove(windowHandle);
            driver.switchTo().window(tabs.get(0));

            // ... and request authentication with honest OP
            String honestInputOpUrl = (String) stepCtx.get(OPParameterConstants.BROWSER_INPUT_HONEST_OP_URL);
            stepCtx.put(OPParameterConstants.BROWSER_INPUT_OP_URL, honestInputOpUrl);
            String submitScript = te.eval(createRPContext(), submitScriptRaw);
            logger.log("Start authentication for Honest OP.");
            driver.executeScript(submitScript);

            // switch focus back to original tab
            driver.switchTo().window(windowHandle);

            // wait a bit to make sure authResp was received
            waitMillis(2000);
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

//        } catch (TimeoutException ex) {
//            logger.log("Timeout while waiting for token request of Honest OP, assuming test passed.");
//            logScreenshot();
//            return TestStepResult.PASS;
//        } catch (ExecutionException ex) {
//            logger.log("Waiting for Honest OP or test result gave an error.", ex);
//            logScreenshot();
//            return TestStepResult.UNDETERMINED;
        } catch (InterruptedException ex) {
            throw new RuntimeException("Test interrupted.", ex);
        }
        return TestStepResult.UNDETERMINED;
    }
}
