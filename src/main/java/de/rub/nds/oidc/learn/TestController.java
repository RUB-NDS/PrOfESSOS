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

import de.rub.nds.oidc.server.ProfConfig;
import de.rub.nds.oidc.server.TestInstanceRegistry;
import de.rub.nds.oidc.test_model.*;
import de.rub.nds.oidc.utils.ImplementationLoadException;
import de.rub.nds.oidc.utils.ValueGenerator;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBElement;


/**
 * @author Tobias Wich
 */
@Path("/")
@Cors
public class TestController {

	private ValueGenerator valueGenerator;
	private TestRunnerRegistry testObjs;
	private TestInstanceRegistry testInsts;
	private ProfConfig profCfg;


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

	@Inject
	public void setProfConfig(ProfConfig cfg) {
		this.profCfg = cfg;
	}

	@OPTIONS
	@Path("/{v1}")
	public void preflight1() {
		System.out.println("Preflight var1 called");
	}
	@OPTIONS
	@Path("/{v1}/{v2}")
	public void preflight2() {
		System.out.println("Preflight var2 called");
	}
	@OPTIONS
	@Path("/{v1}/{v2}/{v3}")
	public void preflight3() {
		System.out.println("Preflight var3 called");
	}
	@OPTIONS
	@Path("/{v1}/{v2}/{v3}/{v4}")
	public void preflight4() {
		System.out.println("Preflight var4 called");
	}

	@GET
	@Path("/{role: rp|op}/{testId}/export-xml")
	@Produces({MediaType.APPLICATION_XML})
	public JAXBElement<TestObjectType> exportXml(@PathParam("testId") String testId) throws NoSuchTestObject {
		return new ObjectFactory().createTestObject(exportJson(testId));
	}

	@GET
	@Path("/{role: rp|op}/{testId}/export-json")
	@Produces({MediaType.APPLICATION_JSON})
	public TestObjectType exportJson(@PathParam("testId") String testId) throws NoSuchTestObject {
		TestRunner obj = testObjs.getTestObject(testId);
		return obj.getTestObj();
	}

	@POST
	@Path("/{role: rp|op}/create-test-object")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes("application/x-www-form-urlencoded")
	public TestObjectType createTestObject(@PathParam("role") String role, @FormParam("test_id") String testId) {

		if (testId == null || !testObjs.isAllowCustomTestIds()) {
			testId = valueGenerator.generateTestId();
		}

		TestRunner runner;
		if (role.equals("rp")) {
			runner = testObjs.createRPTestObject(testId);
		} else {
			runner = testObjs.createOPTestObject(testId);
		}

		var testObj = runner.getTestObj();

		// add default endpoints from config
		var endp = testObj.getServiceEndpoint();
		var endCfg = profCfg.getEndpointCfg();

		addEndpoint(endp, "TestRpUri", endCfg.getTestRPUri().map(v -> v.toString()));
		addEndpoint(endp, "TestOpUri", endCfg.getTestOPUri().map(v -> v.toString()));

		return testObj;
	}

	private void addEndpoint(List<ServiceEndpointType> endp, String name, Optional<String> uri) {
		uri.ifPresent(uriVal -> {
			var obj = new ServiceEndpointType();
			obj.setName(name);
			obj.setURI(uriVal);
			endp.add(obj);
		});
	}

	@POST
	@Path("/delete-test-object")
	@Consumes("application/x-www-form-urlencoded")
	public void deleteTestObject(@FormParam("test_id") String testId) {
		if (testId != null) {
			testObjs.deleteTestObject(testId);
		}
	}

	@POST
	@Path("/rp/{testId}/learn")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public LearnResultType learn(@PathParam("testId") String testId, TestRPConfigType config)
			throws NoSuchTestObject, ImplementationLoadException {
		TestRunner runner = testObjs.getTestObject(testId);

		runner.updateRPConfig(config);
		LearnResultType result = runner.runLearningTest(testInsts);
		return result;
	}

	@POST
	@Path("/op/{testId}/learn")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public LearnResultType learn(@PathParam("testId") String testId, TestOPConfigType config)
			throws NoSuchTestObject, ImplementationLoadException {
		TestRunner runner = testObjs.getTestObject(testId);

		runner.updateOPConfig(config);
		LearnResultType result = runner.runLearningTest(testInsts);
		return result;
	}

	@POST
	@Path("/{role: rp|op}/{testId}/test/{stepId}")
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
	@Path("/{role: rp|op}/{testId}/config")
	@Produces(MediaType.APPLICATION_JSON)
	public TestConfigType getConfig(@PathParam("role") String role, @PathParam("testId") String testId) throws NoSuchTestObject {
		TestRunner obj = testObjs.getTestObject(testId);
		if (role.equals("rp")) {
			obj.getTestObj().getTestConfig().setType(TestRPConfigType.class.getName());
		} else {
			obj.getTestObj().getTestConfig().setType(TestOPConfigType.class.getName());
		}

		return obj.getTestObj().getTestConfig();
	}

	@POST
	@Path("/rp/{testId}/config")
	@Consumes(MediaType.APPLICATION_JSON)
	public void setConfig(@PathParam("testId") String testId, TestRPConfigType config) throws NoSuchTestObject {
		config.setType(TestRPConfigType.class.getName());
		TestRunner obj = testObjs.getTestObject(testId);
		obj.getTestObj().setTestConfig(config);
	}

	@POST
	@Path("/op/{testId}/config")
	@Consumes(MediaType.APPLICATION_JSON)
	public void setConfig(@PathParam("testId") String testId, TestOPConfigType config) throws NoSuchTestObject {
		TestRunner obj = testObjs.getTestObject(testId);
		config.setType(TestOPConfigType.class.getName());
		obj.getTestObj().setTestConfig(config);
	}

	@POST
	@Path("/rp/{testId}/expose/{stepId}")
	@Consumes(MediaType.APPLICATION_JSON)
	public void exposeDiscovery(@PathParam("testId") String testId, @PathParam("stepId") String stepId)
			throws NoSuchTestObject, ImplementationLoadException {
		TestRunner runner = testObjs.getTestObject(testId);

		runner.prepareDiscovery(stepId, testInsts);
	}

}
