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

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.apache.commons.lang3.tuple.Pair;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testng.annotations.BeforeGroups;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static io.restassured.RestAssured.with;
import java.io.FileInputStream;


//public class IntegrationTests implements ITest {
public class IntegrationTests {
	protected DockerComposeContainer docker;
	protected String professosHost;
	protected int professosPort = 0;
	private ThreadLocal<String> testName = new ThreadLocal<>();
	protected RequestSpecification specification;

//  Implementing ITest and  the below methods prevents maven-failsafe 
//  to work correctly, could not find a workaround	
//	@Override
//	public String getTestName() {
//		return testName.get();
//	}
//
//	@BeforeMethod(alwaysRun = true)
//	public void setName(Method method, Object[] testData) {
//		if (testData != null && testData.length > 0) {
//			testName.set(testData[0].toString());
//		}
//	}

	protected Iterator<Object[]> readTestConfigs(String configFile) throws IOException, ParseException {
		String configs = new String(new FileInputStream(configFile).readAllBytes(), "utf-8");
		JSONArray configArray = (JSONArray) new JSONParser(JSONParser.MODE_JSON_SIMPLE).parse(configs);

		Set<Object[]> testData = new LinkedHashSet<>();
		for (Object jo : configArray) {
			String name = (String) ((JSONObject) jo).get("StepName");
			String filename = (String) ((JSONObject) jo).get("LogEntryFile");
			String expect = (String) ((JSONObject) jo).get("Expected");

			testData.add(new Object[]{name, filename, expect});
		}

		return testData.iterator();
	}


	@BeforeGroups(groups = {"docker", "docker-rp", "docker-op"})
	public void startDockerServices() {
		// code that will be invoked when this test is instantiated
		docker = new DockerComposeContainer(new File("./docker/docker-compose.yml"), new File("./docker/docker-compose.it.yml"))
				.withExposedService("prof-apache", 80,
						Wait.forHttp("/").forStatusCode(200))
				.withExposedService("prof", 8080,
						Wait.forHttp("/rp-verifier.html").forStatusCode(200))
				.withExposedService("test-rp", 8080,
						Wait.forHttp("/simple-web-app/login").forStatusCode(200))
				.withExposedService("test-op", 8080,
						Wait.forHttp("/oidc-server").forStatusCode(200));
		// TODO: add regexes, the attempts below didn't match...
//				.waitingFor("professos", Wait.forLogMessage("^.*Deployed \"professos.war\" (runtime-name : \"professos.war\")$", 45))
//				.waitingFor("relying_party", Wait.forLogMessage("^.*Registered web context: '/simple-web-app' for server 'default-server'$", 45))

		// use locally installed compose binary (recommended for building images)
		docker.withLocalCompose(true).withPull(false).start();

		// store service address as exposed to host
		professosHost = docker.getServiceHost("prof", 8080);
		professosPort = docker.getServicePort("prof", 8080);
	}

// Calling stop() is not necessary according to Tescontainers documentation
//	@AfterGroups(groups = {"docker-op", "docker-rp"})
//	public void stopServices() {
//		docker.withLocalCompose(true).stop();
//	}

	protected Pair<String, JSONArray> runTestForLogentry(String testName) {
		System.out.println("Start test: " + testName);
		Response resp =
				with().spec(specification)
					.log().uri()
					.contentType(ContentType.JSON)
					.post("/test/" + testName)
				.then()
					.log().ifError()
					.contentType(ContentType.JSON)
					.statusCode(200)
					.extract().response();
		try {
			JSONObject jsonResult = (JSONObject) new JSONParser(JSONParser.MODE_JSON_SIMPLE).parse(resp.asString());
			String outcome = jsonResult.getAsString("Result");
			JSONArray logentry = (JSONArray) jsonResult.get("LogEntry");

			String logStirng = logentry.toJSONString();

			return Pair.of(outcome, logentry);
		} catch (ParseException e) {
			return Pair.of("NOT RUN", new JSONArray());
		}
	}

}
