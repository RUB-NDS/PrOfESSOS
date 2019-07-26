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

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import net.javacrumbs.jsonunit.JsonAssert;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.ParseException;
import org.apache.commons.lang3.tuple.Pair;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import static io.restassured.RestAssured.*;
import io.restassured.config.LogConfig;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintStream;
import org.testcontainers.shaded.org.apache.commons.io.output.WriterOutputStream;


public class RPVerifierIntegrationTests extends IntegrationTests {
	private HashMap<String, Object> rpConfig;
	private String testsFile;

	@Parameters({"rpTestConfigs"})
	public RPVerifierIntegrationTests(String testsFile) {
		this.testsFile = testsFile;
	}


	@Test(groups = {"rp-it", "docker-rp", "docker"})
	public void setUp() throws IOException {
		// check if testcontainers are used to manage docker services
		RequestSpecBuilder specB = new RequestSpecBuilder();
		if (docker != null && professosHost != null && professosPort != 0) {
			specB.setBaseUri("http://" + professosHost + "/api/rp");
			specB.setPort(professosPort);
		} else {
			// assuming that docker compose was started using default file docker-compose.override.yml
			specB.setBaseUri("http://localhost/api/rp");
			specB.setPort(8080);
		}
		specB.setAccept(ContentType.JSON);
		RequestSpecification spec = specB.build();

		// create new testobject
//		testObject = post("/create-test-object").body().as(TestObjectType.class);
		// ^deserialization does not work, likely due to using upper-case property names
		// and TestConfigType being abstract (might be possible using custom deserializer?)
		System.out.println("Requesting new TestObject");
		Response response =
				with().spec(spec)
//					.log().all().and()
					.post("/create-test-object")
				.then()
//					.log().all()
					.contentType(ContentType.JSON)
					.extract().response();

		// retrieve the test config and test id
		HashMap<String, Object> testObjectMap = response.path("");
		HashMap<String, Object> testConfig = (HashMap<String, Object>) testObjectMap.get("TestConfig");
		String testId = (String) testObjectMap.get("TestId");

		// set configuration to default values of demo SP
		testConfig.put("UrlClientTarget", "https://testrp.org/simple-web-app/login");
		testConfig.put("InputFieldName", "identifier");
		testConfig.put("SeleniumScript", "var opUrl = document.querySelector(\"input[name='identifier']\");\n"
				+ "opUrl.value = \"§browser-input-op_url§\";\n"
				+ "opUrl.form.submit();\n");
		testConfig.put("FinalValidUrl", "https://testrp.org/simple-web-app/");
		testConfig.put("HonestUserNeedle", "{sub=honest-op-test-subject, iss=https://honestop.org/" + testId + "}");
		testConfig.put("EvilUserNeedle", "{sub=evil-op-test-subject, iss=https://evilop.org/" + testId + "}");
		testConfig.put("ProfileUrl", "https://testrp.org/simple-web-app/user");

		String jsonConfig = new JSONObject(testConfig).toJSONString();

		// update RestAssured base URL w/ testID
		spec.basePath(testId);
		defaultParser = Parser.JSON;

		// run learning phase to make sure we are all set and later tests do not
		// trigger unexpected client registrations
		System.out.println("Running learning phase.");

		try (FileWriter fileWriter = new FileWriter("/tmp/logging.txt");
     PrintStream printStream = new PrintStream(new WriterOutputStream(fileWriter), true)) {

    RestAssured.config = RestAssured.config().logConfig(LogConfig.logConfig().defaultStream(printStream));
		String learnResult =
				with().spec(spec)
//					.log().all().and()
					.contentType(ContentType.JSON).and()
					.body(jsonConfig)
					.post("/learn")
				.then()
					.log().all().and()
					.extract().path("TestStepResult.Result");

		Assert.assertEquals(learnResult, "PASS");
		specification = spec;
		}
	}

	@DataProvider(name = "getRPTestConfigs")
	public Iterator<Object[]> getRPTestConfigs() throws IOException, ParseException {
		return readTestConfigs(testsFile);
	}

	@Test(
			groups = {"docker-rp", "rp-it", "docker"},
			dependsOnMethods = {"setUp"},
			dataProvider = "getRPTestConfigs"
	)
	public void testTestStep(String testname, String filename, String expectedResult) throws IOException {
		String expectedJSON = new String(new FileInputStream(filename).readAllBytes(), "utf-8");

		Pair<String, JSONArray> outcome = runTestForLogentry(testname);

		Assert.assertEquals(outcome.getLeft(), expectedResult);
		JsonAssert.assertJsonEquals(expectedJSON, outcome.getRight());
	}
}
