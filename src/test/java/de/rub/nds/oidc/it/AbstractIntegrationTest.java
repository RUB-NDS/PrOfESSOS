package de.rub.nds.oidc.it;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testng.ITest;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.BeforeMethod;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static io.restassured.RestAssured.with;

public abstract class AbstractIntegrationTest implements ITest {
	protected DockerComposeContainer docker;
	protected String professosHost;
	protected int professosPort = 0;
	private ThreadLocal<String> testName = new ThreadLocal<>();

	@Override
	public String getTestName() {
		return testName.get();
	}

	@BeforeMethod(alwaysRun = true)
	public void setName(Method method, Object[] testData) {
		if (testData != null && testData.length > 0) {
			testName.set(testData[0].toString());
		}
	}

	protected Iterator<Object[]> readTestConfigs(String configFile) throws IOException, ParseException {
		String configs = FileUtils.readFileToString(new File(configFile), "utf-8");
		JSONArray configArray = (JSONArray) new JSONParser(JSONParser.MODE_JSON_SIMPLE).parse(configs);

		Set<Object[]> testData = new LinkedHashSet<Object[]>();
		for (Object jo : configArray) {
			String name = (String) ((JSONObject) jo).get("StepName");
			String filename = (String) ((JSONObject) jo).get("LogEntryFile");
			String expect = (String) ((JSONObject) jo).get("Expected");

			testData.add(new Object[]{name, filename, expect});
		}

		return testData.iterator();
	}


	@BeforeGroups(groups = {"docker-rp", "docker-op"})
	public void startDockerServices() {
		// code that will be invoked when this test is instantiated
		docker = new DockerComposeContainer(new File("./docker-compose.override.yml"))
				.withExposedService("professos", 8080,
						Wait.forHttp("/rp-verifier.html").forStatusCode(200))
				.withExposedService("relying_party", 8080,
						Wait.forHttp("/simple-web-app/login").forStatusCode(200))
				.withExposedService("identity_provider", 8080,
						Wait.forHttp("/openid-connect-server-webapp").forStatusCode(200));
		// TODO: add regexes, the attempts below didn't match...
//				.waitingFor("professos", Wait.forLogMessage("^.*Deployed \"professos.war\" (runtime-name : \"professos.war\")$", 45))
//				.waitingFor("relying_party", Wait.forLogMessage("^.*Registered web context: '/simple-web-app' for server 'default-server'$", 45))

		// use locally installed compose binary as we need to build the images
		docker.withLocalCompose(true).start();

		// store service address as exposed to host
		professosHost = docker.getServiceHost("professos", 8080);
		professosPort = docker.getServicePort("professos", 8080);
	}

// Calling stop() is not necessary according to Tescontainers documentation
//	@AfterGroups(groups = {"docker-op", "docker-rp"})
//	public void stopServices() {
//		docker.withLocalCompose(true).stop();
//	}

	protected Pair<String, JSONArray> runTestForLogentry(String testName) {

		Response resp =
				with()
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

//			String logStirng = logentry.toJSONString();

			return Pair.of(outcome, logentry);
		} catch (ParseException e) {
			return Pair.of("NOT RUN", new JSONArray());
		}
	}

}
