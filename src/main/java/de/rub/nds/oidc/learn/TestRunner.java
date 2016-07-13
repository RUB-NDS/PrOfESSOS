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
import de.rub.nds.oidc.server.OPIVConfig;
import de.rub.nds.oidc.server.ServerInstance;
import de.rub.nds.oidc.server.TestInstanceRegistry;
import de.rub.nds.oidc.server.op.OPInstance;
import de.rub.nds.oidc.server.op.OPParameterConstants;
import de.rub.nds.oidc.server.op.OPType;
import de.rub.nds.oidc.test_model.LearnResultType;
import de.rub.nds.oidc.test_model.TestObjectType;
import de.rub.nds.oidc.test_model.TestPlanType;
import de.rub.nds.oidc.test_model.TestRPConfigType;
import de.rub.nds.oidc.test_model.TestStepResult;
import de.rub.nds.oidc.test_model.TestStepResultType;
import de.rub.nds.oidc.test_model.TestStepType;
import de.rub.nds.oidc.utils.ImplementationLoadException;
import de.rub.nds.oidc.utils.ImplementationLoader;
import de.rub.nds.oidc.utils.UriUtils;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

/**
 *
 * @author Tobias Wich
 */
public class TestRunner {

	private final String testId;
	private final OPIVConfig hostCfg;
	private final TestObjectType testObj;
	private final TestPlanType testPlan;
	private final TemplateEngine te;

	private final Map<String, Object> testSuiteCtx;

	public TestRunner(OPIVConfig hostCfg, TestObjectType testObj, TestPlanType testPlan, TemplateEngine te) {
		this.testId = testObj.getTestId();
		this.hostCfg = hostCfg;
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
		}, TestStepResult.UNDETERMINED));

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
		}, TestStepResult.UNDETERMINED));

		LearnResultType learnResult = new LearnResultType();
		learnResult.setTestRPConfig(getTestObj().getTestRPConfig());
		learnResult.setTestStepResult(result);
		return learnResult;
	}

	private <T> T runTestFun(TestInstanceRegistry instReg, TestStepResultType result, Function<BrowserSimulator, T> f,
			T errorResponse)
			throws ImplementationLoadException {
		BrowserSimulator simulator = null;
		TestStepType stepDef = result.getStepReference();
		TestStepLogger logger = new TestStepLogger(result);

		try {
			// setup the test
			Map<String, Object> testStepCtx = Collections.synchronizedMap(new HashMap<>());
			// add parameters to step context
			Optional.ofNullable(testPlan.getSuiteParameters()).ifPresent(sp -> {
				sp.getParameter().forEach(p -> testStepCtx.put(p.getKey(), p.getValue()));
			});
			// overwrite suite parameters
			Optional.ofNullable(stepDef.getTestParameters()).ifPresent(tp -> {
				tp.getParameter().forEach(p -> testStepCtx.put(p.getKey(), p.getValue()));
			});

			// check if the test is authorized by the RP
			if (! testGranted(logger, testStepCtx)) {
				logger.log("Test is not authorized by Relying Party.");
				return errorResponse;
			}

			// RP Test specific config
			if (getTestObj().getTestRPConfig() != null) {
				// resolve OP URL
				String startOpType = stepDef.getBrowserSimulator().getParameter().stream()
						.filter(e -> e.getKey().equals(OPParameterConstants.BROWSER_INPUT_OP_URL))
						.map(e -> e.getValue())
						.findFirst().orElse("EVIL");
				String honestWebfinger = getTestObj().getTestRPConfig().getHonestWebfingerResourceId();
				String evilWebfinger = getTestObj().getTestRPConfig().getEvilWebfingerResourceId();
				// save both in context under their own name
				testStepCtx.put(OPParameterConstants.BROWSER_INPUT_HONEST_OP_URL, honestWebfinger);
				testStepCtx.put(OPParameterConstants.BROWSER_INPUT_EVIL_OP_URL, evilWebfinger);
				// now save standard value
				if (startOpType.equals("HONEST")) {
					testStepCtx.put(OPParameterConstants.BROWSER_INPUT_OP_URL, honestWebfinger);
				} else if (startOpType.equals("EVIL")) {
					testStepCtx.put(OPParameterConstants.BROWSER_INPUT_OP_URL, evilWebfinger);
				} else {
					logger.log("Invalid Browser parameter in test specification.");
					return errorResponse;
				}
			}

			OPInstance op1Inst = new OPInstance(stepDef.getOPConfig1(), logger, testSuiteCtx, testStepCtx, OPType.HONEST);
			instReg.addOP1(testId, new ServerInstance<>(op1Inst, logger));

			OPInstance op2Inst = new OPInstance(stepDef.getOPConfig2(), logger, testSuiteCtx, testStepCtx, OPType.EVIL);
			instReg.addOP2(testId, new ServerInstance<>(op2Inst, logger));

			String browserClass = stepDef.getBrowserSimulator().getImplementationClass();
			simulator = ImplementationLoader.loadClassInstance(browserClass, BrowserSimulator.class);
			simulator.setRpConfig(getTestObj().getTestRPConfig());
			simulator.setTemplateEngine(te);
			simulator.setLogger(logger);
			simulator.setContext(testSuiteCtx, testStepCtx);
			simulator.setParameters(stepDef.getBrowserSimulator().getParameter());

			// run actual test
			return f.apply(simulator);
		} catch (Exception ex) {
			logger.log("Error in simulated browser.", ex);
			return errorResponse;
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

		local.setHonestWebfingerResourceId(rpConfig.getHonestWebfingerResourceId());
		local.setEvilWebfingerResourceId(rpConfig.getEvilWebfingerResourceId());
		local.setUrlClientTarget(rpConfig.getUrlClientTarget());
		local.setInputFieldName(rpConfig.getInputFieldName());
		local.setSeleniumScript(rpConfig.getSeleniumScript());
		local.setHonestUserNeedle(rpConfig.getHonestUserNeedle());
		local.setEvilUserNeedle(rpConfig.getEvilUserNeedle());
		local.setProfileUrl(rpConfig.getProfileUrl());
	}

	private boolean testGranted(TestStepLogger logger, Map<String, Object> testStepCtx) {
		Object grantNotNeeded = testStepCtx.get("RP_grant_not_needed");
		if (grantNotNeeded instanceof String && Boolean.valueOf((String) grantNotNeeded)) {
			logger.log("Permission to perform test on remote server not evaluated.");
			return true;
		} else {
			try {
				String targetUrl = testObj.getTestRPConfig().getUrlClientTarget();
				URI wellKnown = UriBuilder.fromUri(targetUrl)
						.replacePath(".professos")
						.replaceQuery(null)
						.build();

				String grantToken = ClientBuilder.newClient().target(wellKnown).request().get(String.class);
				grantToken = grantToken.trim();

				URI grantTokenUri = UriUtils.normalize(new URI(grantToken));
				URI referenceUri  = UriUtils.normalize(hostCfg.getControllerUri());

				return referenceUri.equals(grantTokenUri);
			} catch (WebApplicationException | URISyntaxException ex) {
				logger.log("Failed to retrieve grant token from Relying Party.", ex);
				return false;
			}
		}
	}

}
