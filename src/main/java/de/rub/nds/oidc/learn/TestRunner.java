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

package de.rub.nds.oidc.learn;

import de.rub.nds.oidc.browser.BrowserSimulator;
import de.rub.nds.oidc.log.TestStepLogger;
import de.rub.nds.oidc.server.ServerInstance;
import de.rub.nds.oidc.server.TestInstanceRegistry;
import de.rub.nds.oidc.server.op.OPInstance;
import de.rub.nds.oidc.server.op.OPType;
import de.rub.nds.oidc.test_model.LearnResultType;
import de.rub.nds.oidc.test_model.TestObjectType;
import de.rub.nds.oidc.test_model.TestPlanType;
import de.rub.nds.oidc.test_model.TestRPConfigType;
import de.rub.nds.oidc.test_model.TestStepResult;
import de.rub.nds.oidc.test_model.TestStepResultType;
import de.rub.nds.oidc.test_model.TestStepType;
import de.rub.nds.oidc.test_model.TestStepType.TestParameters;
import de.rub.nds.oidc.utils.ImplementationLoadException;
import de.rub.nds.oidc.utils.ImplementationLoader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 *
 * @author Tobias Wich
 */
public class TestRunner {

	private final String testId;
	private final TestObjectType testObj;
	private final TestPlanType testPlan;
	private final TemplateEngine te;

	private final Map<String, Object> testSuiteCtx;

	public TestRunner(TestObjectType testObj, TestPlanType testPlan, TemplateEngine te) {
		this.testId = testObj.getTestId();
		this.testObj = testObj;
		this.testPlan = testPlan;
		this.te = te;

		this.testSuiteCtx = Collections.synchronizedMap(new HashMap<>());
	}

	public TestObjectType getTestObj() {
		return testObj;
	}

	public TestPlanType getTestPlan() {
		return testPlan;
	}

	public LearnResultType runLearningTest(TestInstanceRegistry instReg) throws ImplementationLoadException {
		// prepare test result as it is not in the usual report for the learning step
		TestStepType learningStep = getTestPlan().getLearningStep();
		TestStepResultType result = new TestStepResultType();
		result.setStepReference(learningStep);
		result.setResult(TestStepResult.NOT_RUN);

		result.setResult(runTestFun(instReg, result, (simulator) -> {
			return simulator.run();
		}));

		LearnResultType learnResult = new LearnResultType();
		learnResult.setTestRPConfig(getTestObj().getTestRPConfig());
		learnResult.setTestStepResult(result);
		return learnResult;
	}

	public LearnResultType runTest(String testId, TestInstanceRegistry instReg) throws ImplementationLoadException {
		// find matching test
		TestStepResultType result = getTestObj().getTestReport().getTestStepResult().stream()
				.filter(stepResult -> stepResult.getStepReference().getName().equals(testId))
				.findFirst()
				.orElseThrow(() -> new WebApplicationException("Requested test case is not defined.", Response.Status.NOT_FOUND));
		// clear log
		result.getLogEntry().clear();

		result.setResult(runTestFun(instReg, result, (simulator) -> {
			return simulator.run();
		}));

		LearnResultType learnResult = new LearnResultType();
		learnResult.setTestRPConfig(getTestObj().getTestRPConfig());
		learnResult.setTestStepResult(result);
		return learnResult;
	}

	private <T> T runTestFun(TestInstanceRegistry instReg, TestStepResultType result, Function<BrowserSimulator, T> f)
			throws ImplementationLoadException {
		BrowserSimulator simulator = null;
		try {
			// setup the test
			TestStepType stepDef = result.getStepReference();
			TestStepLogger log = new TestStepLogger(result);

			Map<String, Object> testStepCtx = Collections.synchronizedMap(new HashMap<>());
			// add parameters to step context
			Optional.ofNullable(stepDef.getTestParameters()).ifPresent(tp -> {
				tp.getParameter().forEach(p -> testStepCtx.put(p.getKey(), p.getValue()));
			});

			OPInstance op1Inst = new OPInstance(stepDef.getOPConfig1(), log, testSuiteCtx, testStepCtx, OPType.HONEST);
			instReg.addOP1(testId, new ServerInstance<>(op1Inst, log));

			OPInstance op2Inst = new OPInstance(stepDef.getOPConfig2(), log, testSuiteCtx, testStepCtx, OPType.EVIL);
			instReg.addOP2(testId, new ServerInstance<>(op2Inst, log));

			String browserClass = stepDef.getSeleniumScript().getBrowserSimulatorClass();
			simulator = ImplementationLoader.loadClassInstance(browserClass, BrowserSimulator.class);
			simulator.setRpConfig(getTestObj().getTestRPConfig());
			simulator.setTemplateEngine(te);
			simulator.setLogger(log);
			simulator.setContext(testSuiteCtx, testStepCtx);

			// run actual test
			return f.apply(simulator);
		} finally {
			// clean up test
			instReg.removeOP1(testId);
			instReg.removeOP2(testId);

			if (simulator != null) {
				simulator.quit();
			}
		}
	}

	public void updateConfig(TestRPConfigType rpConfig) {
		TestRPConfigType local = getTestObj().getTestRPConfig();

		local.setWebfingerResourceId(rpConfig.getWebfingerResourceId());
		local.setUrlClientTarget(rpConfig.getUrlClientTarget());
		local.setInputFieldName(rpConfig.getInputFieldName());
		local.setSeleniumScript(rpConfig.getSeleniumScript());
		local.setUserNeedle(rpConfig.getUserNeedle());
		local.setProfileUrl(rpConfig.getProfileUrl());
	}

}
