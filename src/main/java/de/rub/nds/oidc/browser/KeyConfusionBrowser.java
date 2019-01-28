package de.rub.nds.oidc.browser;

import de.rub.nds.oidc.server.op.OPContextConstants;
import de.rub.nds.oidc.test_model.TestStepResult;

public class KeyConfusionBrowser extends DefaultRPTestBrowser {

    @Override
	public TestStepResult run() throws InterruptedException {
        TestStepResult result;

        while (! (boolean) stepCtx.getOrDefault(OPContextConstants.MULTI_PART_TEST_FINISHED, false)) {
            // TODO: do we need to clear browser sessions before each test step?
            logger.log("Starting next step of multi part test.");
            result = runTestPart();

            // also fail the test if untrusted URL was requested (SSRF)
            // TODO: only applicable in implcit and hybrid flow
            if (result == TestStepResult.FAIL || (boolean) stepCtx.getOrDefault(OPContextConstants.UNTRUSTED_KEY_REQUESTED, false)) {
                return TestStepResult.FAIL;
            }
        }
        logger.log("Finished last step of multi part test.");
        return TestStepResult.PASS;
    }


    private TestStepResult runTestPart() throws InterruptedException {
        return super.run();
    }

}
