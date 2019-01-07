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

import de.rub.nds.oidc.TestPlanList;
import de.rub.nds.oidc.server.OPIVConfig;
import de.rub.nds.oidc.test_model.*;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 *
 * @author Tobias Wich
 */
@ApplicationScoped
public class TestRunnerRegistry {

	private final Map<String, TestRunner> testObjects;

	private OPIVConfig hosts;
	private TestPlanList planList;
	private TemplateEngine te;

	public TestRunnerRegistry() {
		this.testObjects = new HashMap<>();
	}

	@Inject
	public void setHosts(OPIVConfig hosts) {
		this.hosts = hosts;
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
		// create test object
		TestObjectType to = createTestObject(testId, plan);

		// set both in a testobject instance and save it
		TestRunner toi = new TestRunner(hosts, to, plan, te);
		testObjects.put(testId, toi);

		return toi;
	}

	public TestRunner createOPTestObject(String testId) {
		// load plan
		TestPlanType plan = planList.getOPTestPlan();
		// create test object
		TestObjectType to = createTestObject(testId, plan);

		// set both in a testobject instance and save it
		TestRunner toi = new TestRunner(hosts, to, plan, te);
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
		TestRunner inst = testObjects.get(testId);
		if (inst == null) {
			throw new NoSuchTestObject("Failed to retrieve TestObject for testId " + testId + ".");
		} else {
			return inst;
		}
	}

	private TestRPConfigType createTestRPConfig(String testId) {
		TestRPConfigType testCfg = new TestRPConfigType();
		testCfg.setHonestWebfingerResourceId(hosts.getHonestOPUri() + testId);
		testCfg.setEvilWebfingerResourceId(hosts.getEvilOPUri() + testId);
        testCfg.setType(TestRPConfigType.class.getName());
		return testCfg;
	}

	private TestOPConfigType createTestOPConfig(String testId) {
		TestOPConfigType testCfg = new TestOPConfigType();
        testCfg.setType(TestOPConfigType.class.getName());

//		testCfg.setHonestWebfingerResourceId(hosts.getHonestOPUri() + testId);
//		testCfg.setEvilWebfingerResourceId(hosts.getEvilOPUri() + testId);

		// TODO
		return testCfg;
	}
}
