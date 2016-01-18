/****************************************************************************
 * Copyright 2016 Ruhr-Universität Bochum, Lehrstuhl für Netz- und Datensicherheit.
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

import de.rub.nds.oidc.test_model.ObjectFactory;
import de.rub.nds.oidc.test_model.TestObjectType;
import de.rub.nds.oidc.test_model.TestRPConfigType;
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
	private TestObjectRegistry testObjs;

	@Inject
	public void setValueGenerator(ValueGenerator valueGenerator) {
		this.valueGenerator = valueGenerator;
	}

	@Inject
	public void setTestObjs(TestObjectRegistry testObjs) {
		this.testObjs = testObjs;
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
		TestObjectInstance obj = testObjs.getTestObject(testId);
		return obj.getTestObj();
	}

	@POST
	@Path("/create-test-object")
	public String createTestObject() {
		String testId = valueGenerator.generateTestId();
		testObjs.createRPTestObject(testId);
		return testId;
	}

	@GET
	@Path("/{testId}/config")
	@Produces(MediaType.APPLICATION_JSON)
	public TestRPConfigType getConfig(@PathParam("testId") String testId) throws NoSuchTestObject {
		TestObjectInstance obj = testObjs.getTestObject(testId);
		return obj.getTestObj().getTestRPConfig();
	}

	@POST
	@Path("/{testId}/config")
	@Consumes(MediaType.APPLICATION_JSON)
	public void setConfig(@PathParam("testId") String testId, TestRPConfigType config) throws NoSuchTestObject {
		TestObjectInstance obj = testObjs.getTestObject(testId);
		obj.getTestObj().setTestRPConfig(config);
	}

}
