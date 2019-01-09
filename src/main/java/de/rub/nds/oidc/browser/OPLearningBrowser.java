package de.rub.nds.oidc.browser;

import de.rub.nds.oidc.server.op.OPType;
import de.rub.nds.oidc.server.rp.RPContextConstants;
import de.rub.nds.oidc.server.rp.RPType;
import de.rub.nds.oidc.test_model.RPConfigType;
import de.rub.nds.oidc.test_model.TestStepResult;

public class OPLearningBrowser extends BrowserSimulator {

    @Override
    public final TestStepResult run() throws InterruptedException {
		logger.log("OPLearningBrowser started");
        boolean clientRegistered = ! (boolean) suiteCtx.get(RPContextConstants.CLIENT_REGISTRATION_FAILED);
        if (!clientRegistered) {
            logger.log("Client not registered at OP");
            return TestStepResult.FAIL;
        }

        // register Honest-Client
        TestStepResult result = runUserAuth(RPType.HONEST);
        if (result != TestStepResult.PASS) {
            return result;
        }

        // now do the same for Evil-Client
        loadDriver(true); // will this clear all sessions in chrome-headless? it does for phantomjs...
        result = runUserAuth(RPType.EVIL);

        return result;
    }



    private TestStepResult runUserAuth(RPType rpType) {
		logger.log("run userAuth");

		TestStepResult result = TestStepResult.NOT_RUN;

        //TODO

        return result;
    }



}
