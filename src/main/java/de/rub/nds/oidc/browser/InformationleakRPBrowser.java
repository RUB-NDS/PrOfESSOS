/****************************************************************************
 * Copyright 2016 Ruhr-Universität Bochum.
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

import de.rub.nds.oidc.server.op.OPContextConstants;
import de.rub.nds.oidc.test_model.TestStepResult;
import de.rub.nds.oidc.utils.Misc;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 *
 * @author Tobias Wich
 */
public class InformationleakRPBrowser extends DefaultRPTestBrowser {

	@Override
	public TestStepResult run() {
		stepCtx.put(OPContextConstants.TOKEN_INFORMATIONLEAK_FUTURE, new CompletableFuture<>());
		stepCtx.put(OPContextConstants.USERINFO_INFORMATIONLEAK_FUTURE, new CompletableFuture<>());

		return super.run();
	}

	@Override
	protected TestStepResult checkConditionAfterLogin() {
		logger.log("Checking information leak future for test result values.");
		TestStepResult result1, result2;

		try {
			Object fo = stepCtx.get(OPContextConstants.TOKEN_INFORMATIONLEAK_FUTURE);
			CompletableFuture<TestStepResult> f = (CompletableFuture<TestStepResult>) fo;
			result1 =  f.get(NORMAL_WAIT_TIMEOUT, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			throw new RuntimeException(ex);
		} catch (TimeoutException | ExecutionException ex) {
			result1 = null;
		}

		try {
			Object fo = stepCtx.get(OPContextConstants.USERINFO_INFORMATIONLEAK_FUTURE);
			CompletableFuture<TestStepResult> f = (CompletableFuture<TestStepResult>) fo;
			result2 =  f.get(SEARCH_WAIT_TIMEOUT, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			throw new RuntimeException(ex);
		} catch (TimeoutException | ExecutionException ex) {
			result2 = null;
		}

		return Misc.getWorst(result1, result2);
	}

}
