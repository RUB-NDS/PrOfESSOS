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

package de.rub.nds.oidc;

import de.rub.nds.oidc.test_model.ObjectFactory;
import de.rub.nds.oidc.test_model.TestPlanType;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

/**
 *
 * @author Tobias Wich
 */
@ApplicationScoped
public class TestPlanList {

	private final JAXBContext ctx;
	private final Map<String, String> plans;

	public TestPlanList() throws JAXBException {
		ctx = JAXBContext.newInstance(ObjectFactory.class);
		this.plans = new HashMap<>();

		loadPlan("/testplan/op_test.xml");
		loadPlan("/testplan/rp_test.xml");
	}


	private void loadPlan(String fileName) throws JAXBException {
		InputStream in = getClass().getResourceAsStream(fileName);
		JAXBElement<TestPlanType> wrappedPlan = (JAXBElement<TestPlanType>) ctx.createUnmarshaller().unmarshal(in);
		TestPlanType plan = wrappedPlan.getValue();
		String planName = plan.getName();

		StringWriter w = new StringWriter();
		ctx.createMarshaller().marshal(wrappedPlan, w);
		String planStr = w.toString();

		plans.put(planName, planStr);
	}

	public TestPlanType getTestPlan(String planName) throws NoSuchTestPlan {
		try {
			String planStr = plans.get(planName);
			if (planStr != null) {
				Unmarshaller u = ctx.createUnmarshaller();
				JAXBElement<TestPlanType> plan = (JAXBElement<TestPlanType>) u.unmarshal(new StringReader(planStr));
				return plan.getValue();
			} else {
				throw new NoSuchTestPlan("No TestPlan with name '" + planName + "' available.");
			}
		} catch (JAXBException ex) {
			throw new NoSuchTestPlan("Failed to convert plan.", ex);
		}
	}

	public TestPlanType getRPTestPlan() {
		try {
			return getTestPlan("RP-Test-Plan");
		} catch (NoSuchTestPlan ex) {
			throw new IllegalStateException("Bundled test plan not found.");
		}
	}

	public TestPlanType getOPTestPlan() {
		try {
			return getTestPlan("OP-Test-Plan");
		} catch (NoSuchTestPlan ex) {
			throw new IllegalStateException("Bundled test plan not found.");
		}
	}

}
