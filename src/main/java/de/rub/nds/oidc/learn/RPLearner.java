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

import de.rub.nds.oidc.server.TestInstanceRegistry;
import de.rub.nds.oidc.test_model.LearnResultType;
import de.rub.nds.oidc.test_model.ObjectFactory;
import de.rub.nds.oidc.test_model.TestObjectType;
import de.rub.nds.oidc.test_model.TestRPConfigType;
import de.rub.nds.oidc.test_model.TestResult;
import de.rub.nds.oidc.utils.ImplementationLoadException;
import de.rub.nds.oidc.utils.ValueGenerator;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBElement;

/**
 *
 * @author Tobias Wich
 */
@Path("/rp")
public class RPLearner {

	private ValueGenerator valueGenerator;
	private TestRunnerRegistry testObjs;
	private TestInstanceRegistry testInsts;

	@Inject
	public void setValueGenerator(ValueGenerator valueGenerator) {
		this.valueGenerator = valueGenerator;
	}

	@Inject
	public void setTestObjs(TestRunnerRegistry testObjs) {
		this.testObjs = testObjs;
	}

	@Inject
	public void setTestInsts(TestInstanceRegistry testInsts) {
		this.testInsts = testInsts;
	}


	@GET
	@Path("/{testId}/export")
	@Produces({MediaType.APPLICATION_XML})
	public JAXBElement<TestObjectType> exportXml(@PathParam("testId") String testId) throws NoSuchTestObject {
		return new ObjectFactory().createTestObject(exportJson(testId));
	}
	@GET
	@Path("/{testId}/export")
	@Produces({MediaType.APPLICATION_JSON})
	public TestObjectType exportJson(@PathParam("testId") String testId) throws NoSuchTestObject {
		TestRunner obj = testObjs.getTestObject(testId);
		return obj.getTestObj();
	}

	@POST
	@Path("/create-test-object")
	@Produces(MediaType.APPLICATION_JSON)
	public TestObjectType createTestObject() {
		String testId = valueGenerator.generateTestId();
		TestRunner runner = testObjs.createRPTestObject(testId);

		return runner.getTestObj();
	}

	@POST
	@Path("/{testId}/learn")
	@Consumes({MediaType.APPLICATION_JSON})
	@Produces(MediaType.APPLICATION_JSON)
	public LearnResultType learn(@PathParam("testId") String testId, TestRPConfigType rpConfig)
			throws NoSuchTestObject, ImplementationLoadException {
		TestRunner runner = testObjs.getTestObject(testId);
		runner.updateConfig(rpConfig);

		LearnResultType result = runner.runLearningTest(testInsts);

		return result;
	}

	@POST
	@Path("/{testId}/test/{stepId}")
	@Consumes({MediaType.APPLICATION_JSON})
	@Produces(MediaType.APPLICATION_JSON)
	public TestResult test(@PathParam("testId") String testId, @PathParam("stepId") String stepId)
			throws NoSuchTestObject, ImplementationLoadException {
		TestRunner runner = testObjs.getTestObject(testId);

		LearnResultType result = runner.runTest(stepId, testInsts);

		TestResult wrapper = new TestResult();
		wrapper.setResult(result.getTestStepResult().getResult());
		wrapper.setStepReference(result.getTestStepResult().getStepReference());
		wrapper.getLogEntry().addAll(result.getTestStepResult().getLogEntry());
		return wrapper;
	}

	@GET
	@Path("/{testId}/config")
	@Produces(MediaType.APPLICATION_JSON)
	public TestRPConfigType getConfig(@PathParam("testId") String testId) throws NoSuchTestObject {
		TestRunner obj = testObjs.getTestObject(testId);
		return obj.getTestObj().getTestRPConfig();
	}

	@POST
	@Path("/{testId}/config")
	@Consumes(MediaType.APPLICATION_JSON)
	public void setConfig(@PathParam("testId") String testId, TestRPConfigType config) throws NoSuchTestObject {
		TestRunner obj = testObjs.getTestObject(testId);
		obj.getTestObj().setTestRPConfig(config);
	}

}
