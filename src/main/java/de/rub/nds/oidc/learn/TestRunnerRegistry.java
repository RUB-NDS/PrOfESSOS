/****************************************************************************
 * Copyright 2016-2019 Ruhr-Universität Bochum.
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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.rub.nds.oidc.TestPlanList;
import de.rub.nds.oidc.server.ProfConfig;
import de.rub.nds.oidc.test_model.*;

import javax.annotation.Nonnull;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;


/**
 * @author Tobias Wich
 */
@ApplicationScoped
public class TestRunnerRegistry {

	private Cache<String, TestRunner> testObjects;

	private ProfConfig cfg;
	private TestPlanList planList;
	private TemplateEngine te;

	@PostConstruct
	void init() {
		int maxAge = cfg.getSessionLifetime();
		TimeUnit maxAgeUnit = TimeUnit.MINUTES;
		testObjects = CacheBuilder.newBuilder()
				.expireAfterAccess(maxAge, maxAgeUnit)
				.build();
	}


	@Inject
	public void setConfig(ProfConfig cfg) {
		this.cfg = cfg;
	}

	@Inject
	public void setPlanList(TestPlanList planList) {
		this.planList = planList;
	}

	@Inject
	public void setTemplateEngine(TemplateEngine te) {
		this.te = te;
	}

	public TestRunner createRPTestObject(String testId) {
		// load plan
		TestPlanType plan = planList.getRPTestPlan();
		return registerTestObject(testId, plan);
	}

	public TestRunner createOPTestObject(String testId) {
		// load plan
		TestPlanType plan = planList.getOPTestPlan();
		return registerTestObject(testId, plan);
	}

	private TestRunner registerTestObject(String testId, TestPlanType plan) {
		// create test object
		TestObjectType to = createTestObject(testId, plan);

		// set both in a testobject instance and save it
		TestRunner toi = new TestRunner(cfg, to, plan, te);
		testObjects.put(testId, toi);

		return toi;
	}

	private TestObjectType createTestObject(String testId, TestPlanType plan) {
		// create empty test object
		TestObjectType to = new TestObjectType();
		to.setTestId(testId);

		if (plan.getName().equals("RP-Test-Plan")) {
			to.setTestConfig(createTestRPConfig(testId));
		} else if (plan.getName().equals("OP-Test-Plan")) {
			to.setTestConfig(createTestOPConfig(testId));
		}

		// load plan
		to.setTestPlanReference(plan.getName());

		// prepare report data structure
		to.setTestReport(createEmptyReport(plan));

		return to;
	}

	private TestReportType createEmptyReport(TestPlanType plan) {
		TestReportType report = new TestReportType();

		for (TestStepType nextStep : plan.getTestStep()) {
			TestStepResultType nextResult = new TestStepResultType();
			nextResult.setResult(TestStepResult.NOT_RUN);
			nextResult.setStepReference(nextStep);
			report.getTestStepResult().add(nextResult);
		}

		return report;
	}

	@Nonnull
	public TestRunner getTestObject(@Nonnull String testId) throws NoSuchTestObject {
		TestRunner inst = testObjects.getIfPresent(testId);
		if (inst == null) {
			throw new NoSuchTestObject("Failed to retrieve TestObject for testId " + testId + ".");
		} else {
			return inst;
		}
	}

	protected void deleteTestObject(@Nonnull String testId) {
		testObjects.invalidate(testId);
	}

	public boolean isAllowCustomTestIds() {
		return cfg.getEndpointCfg().isAllowCustomTestIDs();
	} 

	private TestRPConfigType createTestRPConfig(String testId) {
		TestRPConfigType testCfg = new TestRPConfigType();
		testCfg.setType(TestRPConfigType.class.getName());
		testCfg.setHonestWebfingerResourceId(cfg.getEndpointCfg().getHonestOPUri() + testId);
		testCfg.setEvilWebfingerResourceId(cfg.getEndpointCfg().getEvilOPUri() + testId);
		return testCfg;
	}

	private TestOPConfigType createTestOPConfig(String testId) {
		TestOPConfigType testCfg = new TestOPConfigType();
		testCfg.setType(TestOPConfigType.class.getName());
		testCfg.setHonestRpResourceId(cfg.getEndpointCfg().getHonestRPUri() + testId);
		testCfg.setEvilRpResourceId(cfg.getEndpointCfg().getEvilRPUri() + testId);
		return testCfg;
	}
}
