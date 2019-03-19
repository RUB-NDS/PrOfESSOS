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

import com.google.common.base.Strings;
import com.nimbusds.oauth2.sdk.ParseException;
import de.rub.nds.oidc.browser.BrowserSimulator;
import de.rub.nds.oidc.log.TestStepLogger;
import de.rub.nds.oidc.server.OPIVConfig;
import de.rub.nds.oidc.server.ServerInstance;
import de.rub.nds.oidc.server.TestInstanceRegistry;
import de.rub.nds.oidc.server.op.OPContextConstants;
import de.rub.nds.oidc.server.op.OPInstance;
import de.rub.nds.oidc.server.op.OPParameterConstants;
import de.rub.nds.oidc.server.op.OPType;
import de.rub.nds.oidc.server.rp.RPContextConstants;
import de.rub.nds.oidc.server.rp.RPInstance;
import de.rub.nds.oidc.server.rp.RPType;
import de.rub.nds.oidc.test_model.*;
import de.rub.nds.oidc.utils.*;
import org.apache.commons.lang.RandomStringUtils;

import javax.inject.Inject;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
	private ValueGenerator valueGenerator;
	private JsWaiter jsWaiter;

	private final Map<String, Object> testSuiteCtx;

	@Inject
	public TestRunner(OPIVConfig hostCfg, TestObjectType testObj, TestPlanType testPlan, TemplateEngine te) {
		this.testId = testObj.getTestId();
		this.hostCfg = hostCfg;
		this.testObj = testObj;
		this.testPlan = testPlan;
		this.te = te;

		this.testSuiteCtx = Collections.synchronizedMap(new HashMap<>());
	}

	@Inject
	public void setValueGenerator(ValueGenerator valueGenerator) {
		this.valueGenerator = valueGenerator;
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
		learnResult.setTestConfig(getTestObj().getTestConfig());

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
		learnResult.setTestConfig(getTestObj().getTestConfig());
		learnResult.setTestStepResult(result);
		return learnResult;
	}

	private <T> T runTestFun(TestInstanceRegistry instReg, TestStepResultType result, TestFunction<T> f,
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

			boolean success;
			// RP-Verifier specific config
			if (isRPTest()) {
				 success = prepareRPTestStep(instReg, testStepCtx, stepDef, logger);
				if (!success) {
					return errorResponse;
				}
			}

			// OP-Verifier specific config
			if (isOPTest()) {
				success = prepareOPTestStep(instReg, testStepCtx, stepDef, logger);
				if (!success) {
					logger.log("Test step preparation failed.");
					return errorResponse;
				}
			}

			String browserClass = stepDef.getBrowserSimulator().getImplementationClass();
			simulator = ImplementationLoader.loadClassInstance(browserClass, BrowserSimulator.class);
			simulator.setConfig(getTestObj().getTestConfig());
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
			if (isRPTest()) {
				instReg.removeOP1(testId);
				instReg.removeOP2(testId);
			} else if (isOPTest()) {
				instReg.removeRP1(testId);
				instReg.removeRP2(testId);
			}

			if (simulator != null) {
				simulator.quit();
			}
		}
	}


	public void updateRPConfig(TestRPConfigType rpConfig) {
		TestRPConfigType local = (TestRPConfigType) getTestObj().getTestConfig();

		local.setHonestWebfingerResourceId(rpConfig.getHonestWebfingerResourceId());
		local.setEvilWebfingerResourceId(rpConfig.getEvilWebfingerResourceId());
		local.setUrlClientTarget(rpConfig.getUrlClientTarget());
		local.setInputFieldName(rpConfig.getInputFieldName());
		local.setSeleniumScript(rpConfig.getSeleniumScript());
		local.setHonestUserNeedle(rpConfig.getHonestUserNeedle());
		local.setEvilUserNeedle(rpConfig.getEvilUserNeedle());
		local.setProfileUrl(rpConfig.getProfileUrl());
	}

	public void updateOPConfig(TestOPConfigType config) {
		TestOPConfigType testConfig = (TestOPConfigType) getTestObj().getTestConfig();

		testConfig.setAccessToken1(config.getAccessToken1());
		testConfig.setAccessToken2(config.getAccessToken2());
		testConfig.setClient1Config(config.getClient1Config());
		testConfig.setClient2Config(config.getClient2Config());
		testConfig.setConsentScript(config.getConsentScript());
		testConfig.setLoginScript(config.getLoginScript());
		testConfig.setOPMetadata(config.getOPMetadata());
		testConfig.setUrlOPTarget(config.getUrlOPTarget());
		testConfig.setUser1Name(config.getUser1Name());
		testConfig.setUser2Name(config.getUser2Name());
		testConfig.setUser1Pass(config.getUser1Pass());
		testConfig.setUser2Pass(config.getUser2Pass());
	}


	private boolean testGranted(TestStepLogger logger, Map<String, Object> testStepCtx) {
		Object grantNotNeeded = testStepCtx.get("grant_not_needed");
		if (grantNotNeeded instanceof String && Boolean.valueOf((String) grantNotNeeded)) {
			logger.log("Permission to perform test on remote server not evaluated.");
			return true;
		} else {
			try {
				String targetUrl;

				if (isRPTest()) {
					TestRPConfigType testConfig = (TestRPConfigType) testObj.getTestConfig();
					targetUrl = testConfig.getUrlClientTarget();
				} else if (isOPTest()) {
					TestOPConfigType testConfig = (TestOPConfigType) testObj.getTestConfig();
					targetUrl = testConfig.getUrlOPTarget();
				} else {
					logger.log("Unknown TestConfigType, test aborted.");
					return false;
				}

				URI wellKnown = UriBuilder.fromUri(targetUrl)
							.replacePath(".professos")
							.replaceQuery(null)
							.build();

				logger.log("Obtaining permission to perform test from url '" + wellKnown + "'.");
				Response grantTokenResp = ClientBuilder.newClient().target(wellKnown)
						.request().accept(MediaType.WILDCARD).get();
				if (grantTokenResp.getStatus() == 200 && grantTokenResp.getLength() > 0) {
					String grantToken = grantTokenResp.readEntity(String.class);
					if (grantToken == null) {
						// try reading as byte[]. Can happen with Resteasy when no ContentType is set by the server
						grantToken = new String(grantTokenResp.readEntity(byte[].class));
					}
					grantToken = grantToken.trim();

					URI grantTokenUri = UriUtils.normalize(new URI(grantToken));
					URI referenceUri  = UriUtils.normalize(hostCfg.getControllerUri());

					return referenceUri.equals(grantTokenUri);
				} else {
					logger.log("No valid response received.");
					return false;
				}
			} catch (ProcessingException | WebApplicationException | URISyntaxException ex) {
				logger.log("Failed to retrieve grant token from Relying Party.", ex);
				return false;
			}
		}
	}

	public boolean isRPTest() {
		return testObj.getTestConfig().getType().equals(TestRPConfigType.class.getName());
	}

	public boolean isOPTest() {
		return testObj.getTestConfig().getType().equals(TestOPConfigType.class.getName());
	}


	private boolean prepareRPTestStep(TestInstanceRegistry instReg, Map<String, Object> testStepCtx, TestStepType stepDef, TestStepLogger logger ) throws ImplementationLoadException {
		TestRPConfigType testConfig = (TestRPConfigType) getTestObj().getTestConfig();
		// resolve OP URL

		String honestWebfinger;
		String evilWebfinger;
		Boolean enforceReg = Boolean.parseBoolean((String) testStepCtx.get(OPContextConstants.REGISTRATION_NEEDED));
		if (enforceReg) {
			logger.log("attempt to enforce new registration");
			String prefix = "enforce-rp-reg-"; // TODO: this is also used in RequestPath, should be a constant defined somewhere
//			String regEnforcer = prefix + valueGenerator.generateRandString(8);
			String regEnforcer = prefix + RandomStringUtils.randomAlphanumeric(8);
			honestWebfinger = hostCfg.getHonestOPUri().toString() + regEnforcer + "/" + testId;
			evilWebfinger = hostCfg.getEvilOPUri().toString() + regEnforcer + "/" + testId;
			logger.log("Enforcing new registration, set stepcontext URIs to " + evilWebfinger + " and "+ honestWebfinger);
			testStepCtx.put(OPContextConstants.REGISTRATION_ENFORCING_PATH_FRAGMENT, regEnforcer);
		} else {
			honestWebfinger = testConfig.getHonestWebfingerResourceId();
			evilWebfinger = testConfig.getEvilWebfingerResourceId();
		}
		// save both in context under their own name
		testStepCtx.put(OPParameterConstants.BROWSER_INPUT_HONEST_OP_URL, honestWebfinger);
		testStepCtx.put(OPParameterConstants.BROWSER_INPUT_EVIL_OP_URL, evilWebfinger);

		// now save standard value
		String startOpType = stepDef.getBrowserSimulator().getParameter().stream()
				.filter(e -> e.getKey().equals(OPParameterConstants.BROWSER_INPUT_OP_URL))
				.map(e -> e.getValue())
				.findFirst().orElse("EVIL");

		if (startOpType.equals("HONEST")) {
			testStepCtx.put(OPParameterConstants.BROWSER_INPUT_OP_URL, honestWebfinger);
		} else if (startOpType.equals("EVIL")) {
			testStepCtx.put(OPParameterConstants.BROWSER_INPUT_OP_URL, evilWebfinger);
		} else {
			logger.log("Invalid Browser parameter in test specification.");
			return false;
		}

		OPInstance op1Inst = new OPInstance(stepDef.getOPConfig1(), logger, testSuiteCtx, testStepCtx, OPType.HONEST);
		instReg.addOP1(testId, new ServerInstance<>(op1Inst, logger));

		OPInstance op2Inst = new OPInstance(stepDef.getOPConfig2(), logger, testSuiteCtx, testStepCtx, OPType.EVIL);
		instReg.addOP2(testId, new ServerInstance<>(op2Inst, logger));
		return true;
	}

	private boolean prepareOPTestStep(TestInstanceRegistry instReg, Map<String, Object> testStepCtx, TestStepType stepDef,
								   TestStepLogger logger ) throws ImplementationLoadException, IOException, ParseException {
		// TODO
		TestOPConfigType remoteOPConfig = (TestOPConfigType) getTestObj().getTestConfig();
		if (!isMinimalValidOPConfig(remoteOPConfig)) {
			logger.log("Incomplete configuration provided");
			return false;
		}

		testStepCtx.put(RPContextConstants.TARGET_OP_URL, remoteOPConfig.getUrlOPTarget());
		RPInstance rp1Inst = new RPInstance(stepDef.getRPConfig1(), logger, testSuiteCtx, testStepCtx, remoteOPConfig, RPType.HONEST, hostCfg);
		instReg.addRP1(testId, new ServerInstance<>(rp1Inst, logger));

		RPInstance rp2Inst = new RPInstance(stepDef.getRPConfig2(), logger, testSuiteCtx, testStepCtx, remoteOPConfig, RPType.EVIL, hostCfg);
		instReg.addRP2(testId, new ServerInstance<>(rp2Inst, logger));

		boolean isRunnable;
		isRunnable = rp1Inst.getImpl().runTestStepSetup();
		isRunnable &= rp2Inst.getImpl().runTestStepSetup();
		return isRunnable;
	}

	private boolean isMinimalValidOPConfig(TestOPConfigType cfg) {
		boolean areMinRequiredFieldsSet = true;
		areMinRequiredFieldsSet &= !Strings.isNullOrEmpty(cfg.getUser1Name());
		areMinRequiredFieldsSet &= !Strings.isNullOrEmpty(cfg.getUser2Name());
		areMinRequiredFieldsSet &= !Strings.isNullOrEmpty(cfg.getUser1Pass());
		areMinRequiredFieldsSet &= !Strings.isNullOrEmpty(cfg.getUser2Pass());
		areMinRequiredFieldsSet &= !(Strings.isNullOrEmpty(cfg.getUrlOPTarget()) && Strings.isNullOrEmpty(cfg.getOPMetadata()));

		return areMinRequiredFieldsSet;
	}
}
