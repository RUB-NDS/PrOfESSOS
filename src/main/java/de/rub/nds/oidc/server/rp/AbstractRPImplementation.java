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
import com.nimbusds.openid.connect.sdk.op.OIDCProviderConfigurationRequest;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.rp.*;
import de.rub.nds.oidc.log.TestStepLogger;
import de.rub.nds.oidc.server.OPIVConfig;
import de.rub.nds.oidc.test_model.*;
import de.rub.nds.oidc.utils.InstanceParameters;
import net.minidev.json.JSONObject;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
//		2. Otherwise, run dynamic registration (reg Endpoint from discovery)
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
//		logger.log("Client Metadata set");

		BearerAccessToken bearerAccessToken = null;
		String at = getRegistrationAccessToken();
		if (!Strings.isNullOrEmpty(at)) {
			bearerAccessToken = BearerAccessToken.parse("Bearer " + at);
		}

		OIDCClientRegistrationRequest regRequest = new OIDCClientRegistrationRequest(
				getRegistrationEndpoint(),  // triggers discovery if necessary
				clientMetadata,
//				getRegistrationAccessToken()
				bearerAccessToken
		);

		HTTPResponse response = regRequest.toHTTPRequest().send();
		logger.log("Reg request prepared");
		logger.logHttpRequest(regRequest.toHTTPRequest(), regRequest.toHTTPRequest().getQueryAsJSONObject().toString());

		ClientRegistrationResponse regResponse = OIDCClientRegistrationResponseParser.parse(response);

		if (! regResponse.indicatesSuccess()) {
			ClientRegistrationErrorResponse errorResponse = (ClientRegistrationErrorResponse)regResponse;
			logger.log("Dynamic Client Registration failed");
			logger.logHttpResponse(response, response.getContent());
			return TestStepResult.FAIL;
		}

		OIDCClientInformationResponse successResponse = (OIDCClientInformationResponse) regResponse;
		OIDCClientInformation clientInfo = successResponse.getOIDCClientInformation();

		setClientInfo(clientInfo);
		logger.log("Successfully registered client");
		logger.logHttpResponse(response, response.getContent());

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
			logger.log("Failed to parse provided client metadata", e);
			return false;
		}
	}

	private void setClientInfo(OIDCClientInformation info) {
		this.clientInfo = info;
		if (type.equals(RPType.HONEST)) {
			suiteCtx.put(RPContextConstants.HONEST_CLIENT_CLIENTINFO, clientInfo);
		} else {
			suiteCtx.put(RPContextConstants.EVIL_CLIENT_CLIENTINFO, clientInfo);
		}
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

// TODO change logic: first discover if needed, then register if needed.
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

	public TestStepResult discoverOpIfNeeded() throws IOException, ParseException {
		if (opMetaData == null) {
			return discoverRemoteOP();
		}
		logger.log("OP Configuration already retrieved, discovery not required");
		return TestStepResult.PASS;
	}

	private TestStepResult discoverRemoteOP() throws IOException, ParseException {
		logger.log("Running OP discovery");
		Issuer issuer = new Issuer(testOPConfig.getUrlOPTarget());
		OIDCProviderConfigurationRequest request = new OIDCProviderConfigurationRequest(issuer);

		HTTPRequest httpRequest = request.toHTTPRequest();
		HTTPResponse httpResponse = httpRequest.send();
		// TODO check status code, exit if negative
		if (!httpResponse.indicatesSuccess()) {
			logger.log("OP Discovery failed");
			logger.logHttpResponse(httpResponse, httpResponse.getContent());
			return TestStepResult.FAIL;
		}
		logger.log("OP Configuration received");
		logger.logHttpResponse(httpResponse, httpResponse.getContent());

		OIDCProviderMetadata opMetadata = OIDCProviderMetadata.parse(httpResponse.getContentAsJSONObject());
		this.opMetaData = opMetadata;
		suiteCtx.put(RPContextConstants.DISCOVERED_OP_CONFIGURATION, opMetadata);
		return TestStepResult.PASS;
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
