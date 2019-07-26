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

package de.rub.nds.oidc.it;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import net.javacrumbs.jsonunit.JsonAssert;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import static io.restassured.RestAssured.*;

public class OPVerifierIntegrationTests extends IntegrationTests {
	private String testsFile;

	@Parameters({"opTestConfigs"})
	public OPVerifierIntegrationTests(String testsFile) {
		this.testsFile = testsFile;
	}

	@Test(groups = {"op-it", "docker-op", "docker"})
	public void setUp() {
		// check if testcontainers are used to manage docker services
		RequestSpecBuilder specB = new RequestSpecBuilder();
		if (docker != null && professosHost != null && professosPort != 0) {
			specB.setBaseUri("http://" + professosHost + "/api/op");
			specB.setPort(professosPort);
		} else {
			// assuming that docker compose was started using default file docker-compose.override.yml
			specB.setBaseUri("http://localhost/api/op");
			specB.setPort(8080);
		}
		RequestSpecification spec = specB.build();

		System.out.println("Request new TestObject");
		Response response =
				with()
					.spec(spec)
					.post("/create-test-object")
				.then()
					.contentType(ContentType.JSON)
					.extract().response();

		// retrieve the test config and test id
		HashMap<String, Object> testObjectMap = response.path("");
		HashMap<String, Object> testConfig = (HashMap<String, Object>) testObjectMap.get("TestConfig");
		String testId = (String) testObjectMap.get("TestId");

		// set configuration to default values of demo SP
		testConfig.put("UrlOPTarget", "http://honestidp.de:8080/oidc-server");
		testConfig.put("User1Name", "user1");
		testConfig.put("User2Name", "user2");
		testConfig.put("User1Pass", "user1pass");
		testConfig.put("User2Pass", "user2pass");
		testConfig.put("LoginScript", "var username = document.getElementById(\"j_username\");\nusername.value = \"§current_user_username§\";\nvar password = document.getElementById(\"j_password\");\npassword.value = \"§current_user_password§\";\ndocument.forms[0].submit.click();");
		testConfig.put("ConsentScript", "var authBtn = document.confirmationForm.authorize\nauthBtn.click();\n");

		String jsonConfig = new JSONObject(testConfig).toJSONString();

		// update RestAssured base URL w/ testID
		spec.basePath(testId);
//		baseURI = String.format(baseURI + "/" + testId);
		defaultParser = Parser.JSON;

		System.out.println("Run learning phase");
		String learnResult =
				with()
					.spec(spec)
					.contentType(ContentType.JSON).and()
					.body(jsonConfig)
					.post("/learn")
				.then()
					.contentType(ContentType.JSON)
					.log().ifError().and()
					.extract().path("TestStepResult.Result");

		// learning phase should always pass
		Assert.assertEquals(learnResult,"PASS");
		// make request spec available to following teststeps
		specification = spec;
	}

	@DataProvider(name = "getOPTestConfigs")
	public Iterator<Object[]> getOPTestConfigs() throws IOException, ParseException {
		return readTestConfigs(testsFile);
	}

	@Test(
			groups = {"docker-op", "op-it", "docker"},
			dependsOnMethods = {"setUp"},
			dataProvider = "getOPTestConfigs"
	)
	public void testTestStep(String testname, String filename, String expectedResult) throws IOException {
		String expectedJSON = FileUtils.readFileToString(new File(filename), "utf-8");

		Pair<String, JSONArray> outcome = runTestForLogentry(testname);

		Assert.assertEquals(outcome.getLeft(), expectedResult);
		JsonAssert.assertJsonEquals(expectedJSON, outcome.getRight());
	}
}
