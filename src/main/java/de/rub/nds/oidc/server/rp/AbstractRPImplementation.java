package de.rub.nds.oidc.server.rp;

import com.google.common.base.Strings;
import com.nimbusds.oauth2.sdk.GrantType;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.client.ClientInformation;
import com.nimbusds.oauth2.sdk.client.ClientRegistrationErrorResponse;
import com.nimbusds.oauth2.sdk.client.ClientRegistrationResponse;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.util.JSONObjectUtils;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCScopeValue;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderConfigurationRequest;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.rp.*;
import de.rub.nds.oidc.log.TestStepLogger;
import de.rub.nds.oidc.server.OPIVConfig;
import de.rub.nds.oidc.server.op.OPParameterConstants;
import de.rub.nds.oidc.test_model.*;
import de.rub.nds.oidc.utils.InstanceParameters;
import net.minidev.json.JSONObject;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
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
	protected OIDCClientInformation oldClientInfo;
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

	@Override
	public void runTestStepSetup() throws ParseException, IOException {
		boolean success = true;

		success &= discoverOpIfNeeded();
		success &= areStepRequirementsMet();

		// dont run setup steps for RP2 unless neccessary
		if (RPType.HONEST.equals(type)
				|| ! Boolean.valueOf((String) stepCtx.get(RPContextConstants.IS_SINGLE_RP_TEST))
				|| params.getBool(RPParameterConstants.FORCE_REGISTER_CLIENT)) {
			success &= evalConfig();
			success &= registerClientIfNeeded();
			prepareAuthnReq();
		}
		if (RPType.EVIL.equals(type)) {
			stepCtx.put(RPContextConstants.STEP_SETUP_FINISHED, success);
		}
	}

	public void prepareAuthnReq() {

		// TODO: should depend on testStepDefiniton i.e. params.get(), at least in subclasses
		// clientID
		// state
		// authEndp URI
		// nonce
		// scopes
		// response type
		// response mode (whats that?)
//
//		ClaimsRequest claims = new ClaimsRequest();
//		claims.addIDTokenClaim("group");
		AuthenticationRequest authnReq = new AuthenticationRequest.Builder(
				new ResponseType("code","token","id_token"), // TODO: check that these are covered by registered grant types
//				new ResponseType("code"),
				new Scope(OIDCScopeValue.OPENID, OIDCScopeValue.PROFILE),
				clientInfo.getID(),
				getRedirectUri())
				// .state(new State())
				.nonce(new Nonce())
				// .claims(claims)
				.endpointURI(opMetaData.getAuthorizationEndpointURI())
				.build();

		// store in stepCtx, so BrowserSimulator can fetch it
		String currentRP = type == RPType.HONEST ? RPContextConstants.RP1_PREPARED_AUTHNREQ
				: RPContextConstants.RP2_PREPARED_AUTHNREQ;
		stepCtx.put(currentRP, authnReq.toURI());
	}

	public boolean registerClientIfNeeded() throws IOException, ParseException {
		if(params.getBool(RPParameterConstants.FORCE_REGISTER_CLIENT)){
			// don't store new clientConfig in suiteCtx
			return registerClient(type);
		}
		OIDCClientInformation ci = getStoredClientInfo();
		if (ci != null) {
			// use clientInfo from suiteCtx (from user provided config or earlier registration)
			clientInfo = ci;
			return true;
		}
		// otherwise, run dynamic registration
		if (registerClient(type)) {
			storeClientInfo();
			return true;
		}
		return false;
	}


    private boolean registerClient(RPType rpType) throws ParseException, IOException {
		logger.log("Starting client registration");

        OIDCClientMetadata clientMetadata = new OIDCClientMetadata();
		// TODO: grant types need to be configurable in test plan.
		Set<GrantType> grantTypes = new HashSet<>();
		if (!Strings.isNullOrEmpty(params.get(RPParameterConstants.REGISTER_GRANT_TYPES))) {
			// split string on space, build set of GrantTypes using
			// GrantType.parse()
		} else {
			// MitreID demo server does not allow simultanous registration of implicit and authorization_grant
//			grantTypes.add(GrantType.AUTHORIZATION_CODE);
			grantTypes.add(GrantType.IMPLICIT);
			clientMetadata.setGrantTypes(grantTypes);
//			logger.log("grant type implicit requested");
		}
		clientMetadata.setRedirectionURI(getRedirectUri());
		clientMetadata.setName(getClientName());
//		logger.log("Client Metadata set");

		BearerAccessToken bearerAccessToken = null;
		String at = getRegistrationAccessToken();
		if (!Strings.isNullOrEmpty(at)) {
			bearerAccessToken = BearerAccessToken.parse("Bearer " + at);
		}

		OIDCClientRegistrationRequest regRequest = new OIDCClientRegistrationRequest(
//				getRegistrationEndpoint(),
				opMetaData.getRegistrationEndpointURI(),
				clientMetadata,
				bearerAccessToken
		);

		HTTPResponse response = regRequest.toHTTPRequest().send();
		logger.log("Registration request prepared");
		logger.logHttpRequest(regRequest.toHTTPRequest(), regRequest.toHTTPRequest().getQueryAsJSONObject().toString());

		ClientRegistrationResponse regResponse = OIDCClientRegistrationResponseParser.parse(response);

		if (! regResponse.indicatesSuccess()) {
//			ClientRegistrationErrorResponse errorResponse = (ClientRegistrationErrorResponse)regResponse;
			logger.log("Dynamic Client Registration failed");
			logger.logHttpResponse(response, response.getContent());
			return false;
		}

		OIDCClientInformationResponse successResponse = (OIDCClientInformationResponse) regResponse;
		OIDCClientInformation clientInfo = successResponse.getOIDCClientInformation();

		setClientInfo(clientInfo);
		logger.log("Successfully registered client");
		logger.logHttpResponse(response, response.getContent());

		suiteCtx.put(RPContextConstants.CLIENT_REGISTRATION_FAILED, false);

        return true;
    }


	private boolean evalConfig() {
		// attempt to parse user provided ClientConfig JSON strings
		String config = type.equals(RPType.HONEST) ? testOPConfig.getClient1Config() : testOPConfig.getClient2Config();
		if (Strings.isNullOrEmpty(config)) {
			logger.log("Client Config not provided, attempt dynamic registration");
			// not an error
			return true;
		}
		try {
//			Object jsonConfig = new JSONParser(JSONParser.MODE_PERMISSIVE).parse(config);
			JSONObject jsonConfig = JSONObjectUtils.parse(config);
			OIDCClientInformation clientInfo = (OIDCClientInformation) ClientInformation.parse(jsonConfig);
			setClientInfo(clientInfo);
			storeClientInfo();
			return true;
		} catch (com.nimbusds.oauth2.sdk.ParseException  e ) {
			logger.log("Failed to parse provided client metadata", e);
			return false;
		}
	}

	protected void setClientInfo(OIDCClientInformation info) {
		this.clientInfo = info;
	}

	protected OIDCClientInformation getClientInfo() {
		return clientInfo;
	}

	private void storeClientInfo() {
		if (type.equals(RPType.HONEST)) {
			suiteCtx.put(RPContextConstants.HONEST_CLIENT_CLIENTINFO, clientInfo);
		} else {
			suiteCtx.put(RPContextConstants.EVIL_CLIENT_CLIENTINFO, clientInfo);
		}
	}

	private OIDCClientInformation getStoredClientInfo() {
		OIDCClientInformation ci;
		if (type.equals(RPType.HONEST)) {
			ci = (OIDCClientInformation) suiteCtx.get(RPContextConstants.HONEST_CLIENT_CLIENTINFO);
		} else {
			ci = (OIDCClientInformation) suiteCtx.get(RPContextConstants.EVIL_CLIENT_CLIENTINFO);
		}
		return ci;
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
//	private URI getRegistrationEndpoint() throws IOException, IllegalArgumentException, ParseException {
//		URI uri;
//		if (Strings.isNullOrEmpty(testOPConfig.getUrlOPRegistration())) {
//			// user did not provide a registration endpoint, start discovery (based on user provided Issuer Url)
//			discoverRemoteOP();
//			// discovery result has been stored in opMetadata
//			uri = opMetaData.getRegistrationEndpointURI();
//		} else {
//			try {
//				uri = new URI(testOPConfig.getUrlOPRegistration());
//			} catch (URISyntaxException e) {
//				throw new IllegalArgumentException("Failed ot parse Registration Endpoint URI", e);
//			}
//			logger.log(String.format("Using provided Registration Endpoint %s", uri));
//		}
//
//		return uri;
//	}

	public boolean discoverOpIfNeeded() throws IOException, ParseException {

		if (! Strings.isNullOrEmpty(testOPConfig.getOPMetadata()) && opMetaData == null) {
			logger.log("Parsing OP Metadata from provided JSON string");
			opMetaData = OIDCProviderMetadata.parse(testOPConfig.getOPMetadata());
			suiteCtx.put(RPContextConstants.DISCOVERED_OP_CONFIGURATION, opMetaData);
			// throw
		} else if (opMetaData == null) {
			if (suiteCtx.get(RPContextConstants.DISCOVERED_OP_CONFIGURATION) == null) {
				return discoverRemoteOP();
			} else {
				opMetaData = (OIDCProviderMetadata) suiteCtx.get(RPContextConstants.DISCOVERED_OP_CONFIGURATION);
			}
		}
		logger.log("OP Configuration already retrieved, discovery not required");
		return true;
	}

	private boolean discoverRemoteOP() throws IOException, ParseException {
		logger.log("Running OP discovery");
		Issuer issuer = new Issuer(testOPConfig.getUrlOPTarget());
		OIDCProviderConfigurationRequest request = new OIDCProviderConfigurationRequest(issuer);

		HTTPRequest httpRequest = request.toHTTPRequest();
		HTTPResponse httpResponse = httpRequest.send();
		// TODO check status code, exit if negative
		if (!httpResponse.indicatesSuccess()) {
			logger.log("OP Discovery failed");
			logger.logHttpResponse(httpResponse, httpResponse.getContent());
//			stepCtx.put(RPContextConstants.STEP_SETUP_FINISHED, false);
			return false;
		}
		logger.log("OP Configuration received");
		logger.logHttpResponse(httpResponse, httpResponse.getContent());

		OIDCProviderMetadata opMetadata = OIDCProviderMetadata.parse(httpResponse.getContentAsJSONObject());
		this.opMetaData = opMetadata;
		suiteCtx.put(RPContextConstants.DISCOVERED_OP_CONFIGURATION, opMetadata);
		return true;
	}

	private boolean areStepRequirementsMet() {
		// check if RP Parameters provided in TestPlan contradict with user provided configuration

		if (params.getBool(RPParameterConstants.FORCE_REGISTER_CLIENT)) {
			if(Strings.isNullOrEmpty(opMetaData.getRegistrationEndpointURI().toString())) {
				logger.log("TestStep requires dynamic registration but no registration endpoint was configured");
				return false;
			}
		}
		return true;
	}

//
//	public void cleanupAfterTestStep() {
//		if(params.getBool(RPParameterConstants.FORCE_REGISTER_CLIENT) && oldClientInfo != null) {
//			clientInfo = oldClientInfo;
//			oldClientInfo = null;
//		}
//		// TODO: possibly more to cleanup?
//	}

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
