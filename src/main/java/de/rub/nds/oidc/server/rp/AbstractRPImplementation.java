package de.rub.nds.oidc.server.rp;

import com.google.common.base.Strings;
import com.nimbusds.oauth2.sdk.GrantType;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.client.ClientInformation;
import com.nimbusds.oauth2.sdk.client.ClientRegistrationErrorResponse;
import com.nimbusds.oauth2.sdk.client.ClientRegistrationResponse;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.util.JSONObjectUtils;
import com.nimbusds.oauth2.sdk.util.URLUtils;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderConfigurationRequest;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.rp.*;
import de.rub.nds.oidc.log.TestStepLogger;
import de.rub.nds.oidc.server.OPIVConfig;
import de.rub.nds.oidc.test_model.*;
import de.rub.nds.oidc.utils.InstanceParameters;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import okhttp3.*;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static de.rub.nds.oidc.server.rp.RPParameterConstants.*;

public abstract class AbstractRPImplementation implements RPImplementation {

    protected RPConfigType cfg;
	protected OPIVConfig opivCfg;
	protected TestStepLogger logger;
	protected String testId;
	protected URI baseUri;
	protected RPType type;
	protected Map<String, Object> suiteCtx;
	protected Map<String, Object> stepCtx;
	protected InstanceParameters params;
	protected TestOPConfigType testOPConfig;
	protected OIDCClientInformation clientInfo;
	protected OIDCProviderMetadata opMetaData;


	@Override
	public void setRPConfig(RPConfigType cfg) {
		this.cfg = cfg;
	}

	@Override
	public void setOPIVConfig(OPIVConfig opivCfg) {
		this.opivCfg = opivCfg;
	}

	@Override
	public void setLogger(TestStepLogger logger) {
		this.logger = logger;
	}

	@Override
	public void setTestId(String testId) {
		this.testId = testId;
	}

	@Override
	public void setBaseUri(URI baseUri) {
		this.baseUri = baseUri;
	}

	@Override
	public void setRPType(RPType type) {
		this.type = type;
	}

	@Override
	public void setContext(Map<String, Object> suiteCtx, Map<String, Object> stepCtx) {
		this.suiteCtx = suiteCtx;
		this.stepCtx = stepCtx;
	}

	@Override
	public void setParameters(List<ParameterType> params) {
		this.params = new InstanceParameters(params);
	}

	@Override
	public void setTestOPConfig(TestOPConfigType cfg) {
		this.testOPConfig = cfg;
	}

	protected URI getRedirectUri() {
//		return supplyHonestOrEvil(this::getHonestRedirectUri, this::getEvilRedirectUri);
		return type == RPType.HONEST ? getHonestRedirectUri() : getEvilRedirectUri();
	}

	protected URI getEvilRedirectUri() {
		return UriBuilder.fromUri(opivCfg.getEvilRPUri()).path(testId).path(REDIRECT_PATH).build();
	}

	protected URI getHonestRedirectUri() {
		return UriBuilder.fromUri(opivCfg.getHonestRPUri()).path(testId).path(REDIRECT_PATH).build();
	}



	public TestStepResult registerClientIfNeeded() throws IOException, ParseException {
//		1. If the user set a valid ClientConfig in form, use it
//		2. Otherwise, check if a registration endpoint was provided and, if so, register at OP
//		3. Otherwise, check the OP target URL and attempt discovery
		TestStepResult result;

		boolean isValidConfig = evalConfig();
		if (isValidConfig) {
			// user provided Client Config successfully parsed
			result = TestStepResult.PASS;
			suiteCtx.put(RPContextConstants.CLIENT_REGISTRATION_FAILED, false);
			// make sure client_id and client_secret have been set
			// TODO: isnt this implied when parsing the JSON String was successful?
			if(Strings.isNullOrEmpty(clientInfo.getID().getValue())
					|| Strings.isNullOrEmpty(clientInfo.getSecret().getValue())) {

				suiteCtx.put(RPContextConstants.CLIENT_REGISTRATION_FAILED, true);
				result = TestStepResult.FAIL;
			}

			return result;
		}

		// attempt registration - triggers discovery if needed
		result = registerClient(type);
		if (result != TestStepResult.PASS) {
			suiteCtx.put(RPContextConstants.CLIENT_REGISTRATION_FAILED, true);
			return result;
		}

		// client_id and client_secret found, assume we are all set and registered at OP
		suiteCtx.put(RPContextConstants.CLIENT_REGISTRATION_FAILED, false);
		return TestStepResult.PASS;

    }


    private TestStepResult registerClient(RPType rpType) throws ParseException, IOException {
		logger.log("Starting client registration");
        OIDCClientMetadata clientMetadata = new OIDCClientMetadata();
		// TODO: grant types need to be configured in test plan
		clientMetadata.setGrantTypes(Collections.singleton(GrantType.AUTHORIZATION_CODE));
		clientMetadata.setRedirectionURI(getRedirectUri());
		clientMetadata.setName(getClientName());
		logger.log("Client Metadata set");

		BearerAccessToken bat = null;
		String at = getRegistrationAccessToken();
		if (!Strings.isNullOrEmpty(at)) {
			bat = BearerAccessToken.parse("Bearer " + at);
		}

		OIDCClientRegistrationRequest regRequest = new OIDCClientRegistrationRequest(
				getRegistrationEndpoint(),  // triggers discovery if necessary
				clientMetadata,
//				getRegistrationAccessToken()
				bat
		);
		logger.log("Reg request prepared");

//		// TODO
//		// avoid creating several instances, should be singleon
//		OkHttpClient client = new OkHttpClient();
//
//
//		Request.Builder rb = new Request.Builder()
//				.url(getRegistrationEndpoint().toURL())
//				.addHeader("Content-Type", "application/json")
//				.addHeader("Accept", "application/json");
//
//				if (!Strings.isNullOrEmpty(at)) {
//					rb.addHeader("Authorization", at);
//				}
//
//				Request request = rb.method("POST", RequestBody.create(MediaType.parse("application/json; charset=utf-8"), clientMetadata.toString()))
//			 	.build();
//
//		Response response = client.newCall(request).execute();
//
//		if (! response.isSuccessful()) {
//			logger.log("Client Registration failed");
//			logger.log(response.toString());
//			return  TestStepResult.FAIL;
//		}
//
//		JSONObject jsonBody = JSONObjectUtils.parseJSONObject(response.body().string());
//		OIDCClientInformation clientInfo = OIDCClientInformation.parse(jsonBody);
//		logger.log("ClientInfo parsed from registration response");
//		logger.log(response.body().string());


		HTTPResponse response = regRequest.toHTTPRequest().send();

		ClientRegistrationResponse regResponse = OIDCClientRegistrationResponseParser.parse(response);

		if (! regResponse.indicatesSuccess()) {
			ClientRegistrationErrorResponse errorResponse = (ClientRegistrationErrorResponse)regResponse;
			logger.log("Dynamic Client Registration failed");
			logger.log(response.getContent()); // TODO: how can we log a nimbus sdk http response?
			return TestStepResult.FAIL;
		}

		OIDCClientInformationResponse successResponse = (OIDCClientInformationResponse) regResponse;
		OIDCClientInformation clientInfo = successResponse.getOIDCClientInformation();

//
		setClientInfo(clientInfo);
		logger.log(clientInfo.getOIDCMetadata().toJSONObject(true).toJSONString());

		suiteCtx.put(RPContextConstants.CLIENT_REGISTRATION_FAILED, false);
        return TestStepResult.PASS;
    }


	private boolean evalConfig() {
		// attempt to parse user provided ClientConfig JSON strings
		String config = type.equals(RPType.HONEST) ? testOPConfig.getClient1Config() : testOPConfig.getClient2Config();
		if (Strings.isNullOrEmpty(config)) {
			logger.log("Client Config not provided");
			return false;
		}
		try {
//			Object jsonConfig = new JSONParser(JSONParser.MODE_PERMISSIVE).parse(config);
			JSONObject jsonConfig = JSONObjectUtils.parse(config);
			OIDCClientInformation clientInfo = (OIDCClientInformation) ClientInformation.parse(jsonConfig);
			setClientInfo(clientInfo);
			return true;
		} catch (com.nimbusds.oauth2.sdk.ParseException  e ) {
			logger.log("Failed to parse client metadata", e);
			return false;
		}
	}

	private void setClientInfo(OIDCClientInformation info) {
		this.clientInfo = info;
	}

	protected URI getClientUri() {
        URI uri;
        if (params.getBool(FORCE_HONEST_CLIENT_URI)) {
            uri = opivCfg.getHonestRPUri();
        } else {
            uri = type.equals(RPType.HONEST) ? opivCfg.getHonestRPUri() : opivCfg.getEvilRPUri();
        }
        return uri;
    }

	protected String getHonestClientName() {
        return "Honest Client";
    }

    protected String getEvilClientName() {
        return "Evil Client";
    }

    protected String getClientName() {
		return supplyHonestOrEvil(this::getHonestClientName, this::getEvilClientName);
	}


	private String getRegistrationAccessToken() {
		String registrationToken = supplyHonestOrEvil(
				() -> testOPConfig.getAccessToken1(),
				() -> testOPConfig.getAccessToken2()
		);

		return registrationToken;
	}


	private URI getRegistrationEndpoint() throws IOException, IllegalArgumentException, ParseException {
		URI uri;
		if (Strings.isNullOrEmpty(testOPConfig.getUrlOPRegistration())) {
			// user did not provide a registration endpoint, start discovery (based on user provided Issuer Url)
			discoverRemoteOP();
			// discovery result has been stored in opMetadata
			uri = opMetaData.getRegistrationEndpointURI();
		} else {
			try {
				uri = new URI(testOPConfig.getUrlOPRegistration());
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException("Failed ot parse Registration Endpoint URI", e);
			}
			logger.log(String.format("Using provided Registration Endpoint %s", uri));
		}

		return uri;
	}


	private void discoverRemoteOP() throws IOException, ParseException {
		logger.log("Start OP discovery");
		Issuer issuer = new Issuer(testOPConfig.getUrlOPTarget());
		OIDCProviderConfigurationRequest request = new OIDCProviderConfigurationRequest(issuer);

		HTTPRequest httpRequest = request.toHTTPRequest();
		HTTPResponse httpResponse = httpRequest.send();

		OIDCProviderMetadata opMetadata = OIDCProviderMetadata.parse(httpResponse.getContentAsJSONObject());
		this.opMetaData = opMetadata;
		stepCtx.put(RPParameterConstants.DISCOVERED_OP_METADATA, opMetadata);
	}

	protected final <T> T supplyHonestOrEvil(Supplier<T> honestSupplier, Supplier<T> evilSupplier) {
		if (type == RPType.HONEST) {
			return honestSupplier.get();
		} else if (type == RPType.EVIL) {
			return evilSupplier.get();
		} else {
			throw new IllegalStateException("OP is neither honest nor evil.");
		}
	}

//    protected URI getRedirectURI() {
//        URI uri;
//        if (params.getBool(FORCE_HONEST_REDIRECT_URI)) {
//            uri = getHonestRedirectUri();
//        } else {
//            uri = type.equals(RPType.HONEST) ? getHonestRedirectUri() : getEvilRedirectUri();
//        }
//        return uri;
//    }
//
//    protected URI getHonestRedirectUri() {
//
//    }
//
//    protected URI getEvilRedirectUri() {
//
//    }


}
