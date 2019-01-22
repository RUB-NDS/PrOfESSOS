package de.rub.nds.oidc.browser;

import de.rub.nds.oidc.server.op.OPContextConstants;
import de.rub.nds.oidc.test_model.TestStepResult;

public class MultiStepTestBrowser extends DefaultRPTestBrowser {

    @Override
	public TestStepResult run() throws InterruptedException {
        TestStepResult result;

        while (! (boolean) stepCtx.getOrDefault(OPContextConstants.MULTI_PART_TEST_FINISHED, false)) {
            logger.log("Starting next step of multi part test.");
            result = runTestPart();

            // also fail the test if untrusted URL was requested (SSRF)
            // TODO: only applicable in implcit and hybrid flow
            if (result == TestStepResult.FAIL || (boolean) stepCtx.getOrDefault(OPContextConstants.UNTRUSTED_KEY_REQUESTED, false)) {
                return TestStepResult.FAIL;
            }
            // TODO: do we really need to clear browser sessions before each test step?
            loadDriver(true);
            // TODO: check if stepCtx needs clean up
        }
        logger.log("Finished last step of multi part test.");
        return TestStepResult.PASS;
    }


    private TestStepResult runTestPart() throws InterruptedException {
        return super.run();
    }

}
