package de.rub.nds.oidc.browser;

import de.rub.nds.oidc.server.op.OPContextConstants;
import de.rub.nds.oidc.server.op.OPParameterConstants;
import de.rub.nds.oidc.test_model.TestStepResult;
import org.openqa.selenium.WebDriver;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SessionOverwritingOPBrowser extends DefaultRPTestBrowser {

    @Override
    public TestStepResult run() {
        try {
            // prepare futures
            CompletableFuture<?> waitForEvil = new CompletableFuture<>();
            stepCtx.put(OPContextConstants.BLOCK_HONEST_OP_FUTURE, waitForEvil);
            CompletableFuture<TestStepResult> blockAndResult = new CompletableFuture<>();
            stepCtx.put(OPContextConstants.BLOCK_BROWSER_AND_TEST_RESULT, blockAndResult);

            // open clients login page
            String startUrl = rpConfig.getUrlClientTarget();
            logger.log(String.format("Opening browser with URL '%s'.", startUrl));
            driver.get(startUrl);

            // execute JS to start authentication
            String honestInputOpUrl = (String) stepCtx.get(OPParameterConstants.BROWSER_INPUT_HONEST_OP_URL);
            stepCtx.put(OPParameterConstants.BROWSER_INPUT_OP_URL, honestInputOpUrl);
            String submitScriptRaw = rpConfig.getSeleniumScript();
            String submitScript = te.eval(createRPContext(), submitScriptRaw);
            logger.log("Start authentication for Honest OP.");
            driver.executeScript(submitScript);

            // then open second browser window...
            String windowHandle = driver.getWindowHandle();
//            logger.log(String.format("Opening new browser window with URL '%s'.", startUrl));
            driver.executeScript("window.open('" + startUrl + "', '_blank');");

            // switch to newly openened window
            ArrayList<String> tabs = new ArrayList(driver.getWindowHandles());
//            logger.log(String.format("number of open tabs: %s", tabs.size()));
            tabs.remove(windowHandle);
            driver.switchTo().window(tabs.get(0));

            // ... and request authentication with Evil OP
            String evilInputOpUrl = (String) stepCtx.get(OPParameterConstants.BROWSER_INPUT_EVIL_OP_URL);
            stepCtx.put(OPParameterConstants.BROWSER_INPUT_OP_URL, evilInputOpUrl);
            String submitScriptEvil = te.eval(createRPContext(), submitScriptRaw);
            logger.log("Start authentication for Evil OP.");
            driver.executeScript(submitScriptEvil);

            // switch focus back to original tab
            driver.switchTo().window(windowHandle);

            // wait for result of the test
            TestStepResult result = blockAndResult.get(10, TimeUnit.SECONDS);
            logScreenshot();
            logger.log("Authentication result for Honest OP.");
            return result;
        } catch (TimeoutException ex) {
            logger.log("Timeout while waiting for token request of Honest OP, assuming test passed.");
            logScreenshot();
            return TestStepResult.PASS;
        } catch (ExecutionException  ex) {
            logger.log("Waiting for Honest OP or test result gave an error.", ex);
            logScreenshot();
            return TestStepResult.UNDETERMINED;
        } catch (InterruptedException ex) {
            throw new RuntimeException("Test interrupted.", ex);
        }
    }
}
