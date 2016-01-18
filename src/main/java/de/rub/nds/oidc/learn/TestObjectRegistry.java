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
import de.rub.nds.oidc.test_model.TestObjectType;
import de.rub.nds.oidc.test_model.TestPlanType;
import de.rub.nds.oidc.test_model.TestRPConfigType;
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
public class TestObjectRegistry {

	private final Map<String, TestObjectInstance> testObjects;

	private TestPlanList planList;

	public TestObjectRegistry() {
		this.testObjects = new HashMap<>();
	}

	@Inject
	public void setPlanList(TestPlanList planList) {
		this.planList = planList;
	}

	public TestObjectInstance createRPTestObject(String testId) {
		// create empty test object
		TestObjectType to = new TestObjectType();
		to.setTestId(testId);
		to.setTestRPConfig(new TestRPConfigType());

		// load plan
		TestPlanType plan = planList.getRPTestPlan();
		to.setTestPlanReference(plan.getName());

		// set both in a testobject instance and save it
		TestObjectInstance toi = new TestObjectInstance(to, plan);
		testObjects.put(testId, toi);

		return toi;
	}

	@Nonnull
	public TestObjectInstance getTestObject(@Nonnull String testId) throws NoSuchTestObject {
		TestObjectInstance inst = testObjects.get(testId);
		if (inst == null) {
			throw new NoSuchTestObject("Failed to retrieve TestObject for testId " + testId + ".");
		} else {
			return inst;
		}
	}

}
