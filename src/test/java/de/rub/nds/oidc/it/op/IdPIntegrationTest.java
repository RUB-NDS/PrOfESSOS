package de.rub.nds.oidc.it.op;

import de.rub.nds.oidc.it.AbstractIntegrationTest;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import io.restassured.response.Response;
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

public class IdPIntegrationTest extends AbstractIntegrationTest {
	private String testsFile;

	@Parameters({"opTestConfigs"})
	public IdPIntegrationTest(String testsFile) {
		this.testsFile = testsFile;
	}

	@Test(groups = {"op-it", "docker-op"})
	public void setUp() {
		// check if testcontainers are used to manage docker services
		if (docker != null && professosHost != null && professosPort != 0) {
			baseURI = "http://" + professosHost + "/api/op";
			port = professosPort;
		} else {
			// assuming that docker compose was started using default file docker-compose.override.yml
			baseURI = "http://localhost/api/op";
			port = 8080;
		}

		Response response =
				with()
						.post("/create-test-object")
						.then()
						.contentType(ContentType.JSON)
						.extract().response();

		// retrieve the test config and test id
		HashMap<String, Object> testObjectMap = response.path("");
		HashMap<String, Object> testConfig = (HashMap<String, Object>) testObjectMap.get("TestConfig");
		String testId = (String) testObjectMap.get("TestId");

		// set configuration to default values of demo SP
		testConfig.put("UrlOPTarget", "http://honestidp.de:8080/openid-connect-server-webapp");
		testConfig.put("User1Name", "user1");
		testConfig.put("User2Name", "user2");
		testConfig.put("User1Pass", "user1pass");
		testConfig.put("User2Pass", "user2pass");
		testConfig.put("LoginScript", "var username = document.getElementById(\"j_username\");\nusername.value = \"§current_user_username§\";\nvar password = document.getElementById(\"j_password\");\npassword.value = \"§current_user_password§\";\ndocument.forms[0].submit.click();");
		testConfig.put("ConsentScript", "var authBtn = document.confirmationForm.authorize\nauthBtn.click();\n");

		String jsonConfig = new JSONObject(testConfig).toJSONString();

		// update RestAssured base URL w/ testID
		baseURI = String.format(baseURI + "/" + testId);
		defaultParser = Parser.JSON;

		String learnResult =
				with()
					.contentType(ContentType.JSON).and()
					.body(jsonConfig)
					.post("/learn")
				.then()
					.extract().path("TestStepResult.Result");

		// learning phase should always pass
		Assert.assertEquals("PASS", learnResult);
	}

	@DataProvider(name = "getOPTestConfigs")
	public Iterator<Object[]> getOPTestConfigs() throws IOException, ParseException {
		return readTestConfigs(testsFile);
	}

	@Test(
			groups = {"docker-op", "op-it"},
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
