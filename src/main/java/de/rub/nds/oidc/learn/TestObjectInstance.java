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

import de.rub.nds.oidc.test_model.TestObjectType;
import de.rub.nds.oidc.test_model.TestPlanType;

/**
 *
 * @author Tobias Wich
 */
public class TestObjectInstance {

	private final TestObjectType testObj;
	private final TestPlanType testPlan;

	public TestObjectInstance(TestObjectType testObj, TestPlanType testPlan) {
		this.testObj = testObj;
		this.testPlan = testPlan;
	}

	public TestObjectType getTestObj() {
		return testObj;
	}

	public TestPlanType getTestPlan() {
		return testPlan;
	}

}
