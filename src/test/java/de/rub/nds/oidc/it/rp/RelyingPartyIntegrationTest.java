package de.rub.nds.oidc.it.rp;

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

public class RelyingPartyIntegrationTest extends AbstractIntegrationTest {
	private HashMap<String, Object> rpConfig;
	private String testsFile;

	@Parameters({"rpTestConfigs"})
	public RelyingPartyIntegrationTest(String testsFile) {
		this.testsFile = testsFile;
	}


	@Test(groups = {"rp-it", "docker-rp"})
	public void setUp() {
		// check if testcontainers are used to manage docker services
		if (docker != null && professosHost != null && professosPort != 0) {
			baseURI = "http://" + professosHost + "/api/rp";
			port = professosPort;
		} else {
			// assuming that docker compose was started using default file docker-compose.override.yml
			baseURI = "http://localhost/api/rp";
			port = 8080;
		}

		// create new testobject
//		testObject = post("/create-test-object").body().as(TestObjectType.class);
		// ^deserialization does not work, likely due to using upper-case property names
		// and TestConfigType being abstract (might be possible using custom deserializer?)

		Response response =
				with()
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
		testConfig.put("UrlClientTarget", "http://www.honestsp.de:8080/simple-web-app/login");
		testConfig.put("InputFieldName", "identifier");
		testConfig.put("SeleniumScript", "var opUrl = document.querySelector(\"input[name='identifier']\");\nopUrl.value = \"§browser-input-op_url§\";\nopUrl.form.submit();\n");
		testConfig.put("FinalValidUrl", "http://www.honestsp.de:8080/simple-web-app/");
		testConfig.put("HonestUserNeedle", "{sub=honest-op-test-subject, iss=http://idp.oidc.honest-sso.de:8080/dispatch/" + testId + "}");
		testConfig.put("EvilUserNeedle", "{sub=evil-op-test-subject, iss=http://idp.oidc.attack-sso.de:8080/dispatch/" + testId + "}");
		testConfig.put("ProfileUrl", "http://www.honestsp.de:8080/simple-web-app/user");

		String jsonConfig = new JSONObject(testConfig).toJSONString();

		// update RestAssured base URL w/ testID
		baseURI = String.format(baseURI + "/" + testId);
		defaultParser = Parser.JSON;

		// run learning phase to make sure we are all set and later tests do not
		// trigger unexpected client registrations
		String learnResult =
				with()
//					.log().all().and()
						.contentType(ContentType.JSON).and()
						.body(jsonConfig)
						.post("/learn")
						.then()
//					.log().all().and()
//					.log().ifError().and()
						.extract().path("TestStepResult.Result");

		Assert.assertEquals("PASS", learnResult);
	}

	@DataProvider(name = "getRPTestConfigs")
	public Iterator<Object[]> getRPTestConfigs() throws IOException, ParseException {
		return readTestConfigs(testsFile);
	}

	@Test(
			groups = {"docker-rp", "rp-it"},
			dependsOnMethods = {"setUp"},
			dataProvider = "getRPTestConfigs"
	)
	public void testTestStep(String testname, String filename, String expectedResult) throws IOException {
		String expectedJSON = FileUtils.readFileToString(new File(filename), "utf-8");

		Pair<String, JSONArray> outcome = runTestForLogentry(testname);

		Assert.assertEquals(outcome.getLeft(), expectedResult);
		JsonAssert.assertJsonEquals(expectedJSON, outcome.getRight());
	}
}
