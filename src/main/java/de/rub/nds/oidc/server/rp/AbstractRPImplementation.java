package de.rub.nds.oidc.server.rp;

import com.google.common.base.Strings;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.client.ClientRegistrationResponse;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallenge;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.util.JSONObjectUtils;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.ClaimsRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.Prompt;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderConfigurationRequest;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.rp.*;
import de.rub.nds.oidc.log.TestStepLogger;
import de.rub.nds.oidc.server.OPIVConfig;
import de.rub.nds.oidc.server.op.OPContextConstants;
import de.rub.nds.oidc.server.op.OPParameterConstants;
import de.rub.nds.oidc.test_model.ParameterType;
import de.rub.nds.oidc.test_model.RPConfigType;
import de.rub.nds.oidc.test_model.TestOPConfigType;
import de.rub.nds.oidc.utils.InstanceParameters;
import de.rub.nds.oidc.utils.UnsafeOIDCProviderMetadata;
import net.minidev.json.JSONObject;
import org.apache.commons.lang3.RandomStringUtils;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;


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
	protected UnsafeOIDCProviderMetadata opMetaData;


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
		return type == RPType.HONEST ? getHonestRedirectUri() : getEvilRedirectUri();
	}

	protected URI getEvilRedirectUri() {
		return UriBuilder.fromUri(opivCfg.getEvilRPUri()).path(testId).path(REDIRECT_PATH).build();
	}

	protected URI getHonestRedirectUri() {
		return UriBuilder.fromUri(opivCfg.getHonestRPUri()).path(testId).path(REDIRECT_PATH).build();
	}


	protected URI manipulateURI(URI uri, boolean addSubdomain, boolean addPath, boolean addTld) {
		String rnd = RandomStringUtils.randomAlphanumeric(12);
		rnd = rnd.toLowerCase();

		UriBuilder ub = UriBuilder.fromUri(uri);
		String newHost = uri.getHost();
		if (addSubdomain) {
			newHost = rnd + "." + newHost;
		}
		if (addTld) {
			newHost = newHost + ".invalid";
		}
		ub.host(newHost);

		if (addPath) {
			ub.path(rnd);
		}
		URI result = ub.build();

		// store for later comparison
		stepCtx.put(RPContextConstants.REDIRECT_URI_MANIPULATOR, rnd);
		stepCtx.put(RPContextConstants.MANIPULATED_REDIRECT_URI, result);
		return result;
	}


	@Override
	public boolean runTestStepSetup() throws ParseException, IOException {
		boolean success = true;

		success &= areStepRequirementsMet();

		// dont run setup steps for RP2 unless neccessary
		if (RPType.HONEST.equals(type)
				|| !Boolean.parseBoolean((String) stepCtx.get(RPParameterConstants.IS_SINGLE_RP_TEST))
				|| Boolean.parseBoolean((String) stepCtx.get(RPParameterConstants.FORCE_CLIENT_REGISTRATION))
				|| Boolean.parseBoolean((String) stepCtx.get(RPContextConstants.IS_RP_LEARNING_STEP))) {

			success &= discoverOpIfNeeded();
			success &= registerClientIfNeeded();
			success &= isValidClientConfig();
			if (success) {
				prepareAuthnReq();
			}
		}
		if (RPType.EVIL.equals(type)) {
			stepCtx.put(RPContextConstants.RP1_PREPARED_REDIRECT_URI, getHonestRedirectUri());
			stepCtx.put(RPContextConstants.RP2_PREPARED_REDIRECT_URI, getEvilRedirectUri());
			stepCtx.put(RPContextConstants.STEP_SETUP_FINISHED, success);
		}
		return success;
	}

	@Override
	public void prepareAuthnReq() {
		ResponseType rt = getAuthReqResponseType();
		Scope scope = getAuthReqScope();
		ClientID clientID = getAuthReqClientID();
		URI redirectURI = getAuthReqRedirectUri();

		AuthenticationRequest.Builder ab = new AuthenticationRequest.Builder(
				rt,
				scope,
				clientID,
				redirectURI
		);
		ab.state(getAuthReqState());
		ab.nonce(getAuthReqNonce());
		ab.responseMode(getAuthReqResponseMode());
		ab.prompt(getAuthReqPrompt());
		ab.claims(getAuthReqClaims());
		ab.endpointURI(opMetaData.getAuthorizationEndpointURI());

		AuthenticationRequest authnReq = ab.build();
		URI authnReqUri = applyPkceParamstoAuthReqUri(authnReq.toURI());
		authnReqUri = applyIdTokenHintToAuthReqUri(authnReqUri);

		// make prepared request available for the browser
		String currentRP = type == RPType.HONEST ? RPContextConstants.RP1_PREPARED_AUTHNREQ
				: RPContextConstants.RP2_PREPARED_AUTHNREQ;
		stepCtx.put(currentRP, authnReqUri);
	}

	// util method because nimbus SDK does not allow
	// 'wrong' or empty  method parameters
	protected URI applyPkceParamstoAuthReqUri(URI uri) {
		CodeVerifier cv = getCodeChallengeVerifier();
		CodeChallengeMethod cm = getCodeChallengeMethod();
		if (cv == null && cm == null) {
			return uri;
		}

		UriBuilder ub = UriBuilder.fromUri(uri);
		if (cm != null) {
			ub.queryParam("code_challenge_method", cm.getValue());
		} else {
			// don't add method param but set to default
			cm = CodeChallengeMethod.S256;
		}
		if (cv != null) {
			ub.queryParam("code_challenge", CodeChallenge.compute(cm, cv).getValue());
		}

		return ub.build();
	}

	protected URI applyIdTokenHintToAuthReqUri(URI uri) {
		// default: do nothing
		return uri;
	}

	protected abstract URI getAuthReqRedirectUri();

	protected abstract URI getTokenReqRedirectUri();

	protected abstract ResponseType getAuthReqResponseType();

	protected abstract Scope getAuthReqScope();

	protected abstract ClientID getAuthReqClientID();

	@Nullable
	protected abstract State getAuthReqState();

	@Nullable
	protected abstract Nonce getAuthReqNonce();

	@Nullable
	protected abstract ResponseMode getAuthReqResponseMode();

	@Nullable
	protected abstract Prompt getAuthReqPrompt();

	@Nullable
	protected abstract ClaimsRequest getAuthReqClaims();

	@Nullable
	protected abstract CodeVerifier getCodeChallengeVerifier();

	@Nullable
	protected abstract CodeChallengeMethod getCodeChallengeMethod();

	@Nullable
	protected abstract JWT getIdTokenHint();

	@Nullable
	protected abstract ClientAuthenticationMethod getRegistrationClientAuthMethod();

	protected abstract HashSet<ResponseType> getRegistrationResponseTypes();

	protected abstract HashSet<GrantType> getRegistrationGrantTypes();


	private boolean registerClientIfNeeded() throws IOException, ParseException {
		boolean isRegistrationSupported = opMetaData != null && opMetaData.getRegistrationEndpointURI() != null;

		boolean isClientConfigProvided = false;
		if (!Strings.isNullOrEmpty(testOPConfig.getClient1Config())
				&& !Strings.isNullOrEmpty(testOPConfig.getClient2Config())) {
			isClientConfigProvided = true;
		}
		
		if (isClientConfigProvided && Boolean.parseBoolean((String) stepCtx.get(RPContextConstants.IS_RP_LEARNING_STEP))) {
			return true;
		}

		if (Boolean.parseBoolean((String) stepCtx.get(RPParameterConstants.FORCE_CLIENT_REGISTRATION))) {
			if (!isRegistrationSupported) {
				logger.log("TestStep requires dynamic registration, which is not supported acc. to configuration.");
				return false;
			}
			// run registration for current testStep but do not store new clientConfig in suiteCtx
			return registerClient(type);
		}
		
		// default: use clientInfo from suiteCtx (from previous test; user provided config or earlier registration)
		OIDCClientInformation ci = getStoredClientInfo(type == RPType.HONEST);
		if (ci != null) {
			setClientInfo(ci);
			return true;
		}

		// otherwise, run dynamic registration
		if (registerClient(type)) {
			// store registration response in suiteCtx
			storeClientInfo();
			return true;
		}
		return false;
	}


	private boolean registerClient(RPType rpType) throws ParseException, IOException {
		logger.log("Starting client registration");

		OIDCClientMetadata clientMetadata = new OIDCClientMetadata();

		Set<GrantType> grantTypes = getRegistrationGrantTypes();
		clientMetadata.setGrantTypes(grantTypes);

		clientMetadata.setRedirectionURI(getRedirectUri());
		clientMetadata.setName(getClientName());

		Set<ResponseType> responseType = getRegistrationResponseTypes();
		clientMetadata.setResponseTypes(responseType);

		ClientAuthenticationMethod tokenEndpointAuthMethod = getRegistrationClientAuthMethod();
		clientMetadata.setTokenEndpointAuthMethod(tokenEndpointAuthMethod);

		BearerAccessToken bearerAccessToken = null;
		String at = getRegistrationAccessToken();
		if (!Strings.isNullOrEmpty(at)) {
			bearerAccessToken = BearerAccessToken.parse("Bearer " + at);
		}

		OIDCClientRegistrationRequest regRequest = new OIDCClientRegistrationRequest(
				opMetaData.getRegistrationEndpointURI(),
				clientMetadata,
				bearerAccessToken
		);

		HTTPResponse response = regRequest.toHTTPRequest().send();
		logger.log("Registration request prepared");
		logger.logHttpRequest(regRequest.toHTTPRequest(), regRequest.toHTTPRequest().getQueryAsJSONObject().toString());

		ClientRegistrationResponse regResponse = OIDCClientRegistrationResponseParser.parse(response);

		if (!regResponse.indicatesSuccess()) {
			logger.log("Dynamic Client Registration failed");
			logger.logHttpResponse(response, response.getContent());
			return false;
		}

		OIDCClientInformationResponse successResponse = (OIDCClientInformationResponse) regResponse;
		OIDCClientInformation clientInfo = successResponse.getOIDCClientInformation();

		setClientInfo(clientInfo);
		logger.log("Successfully registered client");
		logger.logHttpResponse(response, response.getContent());

		return true;
	}


	private boolean isValidClientConfig() {

		if (getClientInfo() != null) {
			// stored info found, either retrieved by registration or already vetted
			return true;
		}
		
		// attempt to parse user provided ClientConfig JSON strings
		String config = type.equals(RPType.HONEST) ? testOPConfig.getClient1Config() : testOPConfig.getClient2Config();
		if (Strings.isNullOrEmpty(config)) {
			// no client configuration provided
			return false;
		}
		try {
			JSONObject jsonConfig = JSONObjectUtils.parse(config);
			OIDCClientInformation clientInfo = OIDCClientInformation.parse(jsonConfig);
			setClientInfo(clientInfo);
			storeClientInfo();
			return true;
		} catch (com.nimbusds.oauth2.sdk.ParseException e) {
			logger.log("Failed to parse provided client metadata", e);
			return false;
		}
	}

	protected OIDCClientInformation getClientInfo() {
		return clientInfo;
	}

	protected void setClientInfo(OIDCClientInformation info) {
		clientInfo = info;
	}

	private void storeClientInfo() {
		if (type.equals(RPType.HONEST)) {
			suiteCtx.put(RPContextConstants.HONEST_CLIENT_CLIENTINFO, clientInfo);
		} else {
			suiteCtx.put(RPContextConstants.EVIL_CLIENT_CLIENTINFO, clientInfo);
		}
	}

	private OIDCClientInformation getStoredClientInfo(boolean ofHonestClient) {
		OIDCClientInformation ci;
		if (ofHonestClient) {
			ci = (OIDCClientInformation) suiteCtx.get(RPContextConstants.HONEST_CLIENT_CLIENTINFO);
		} else {
			ci = (OIDCClientInformation) suiteCtx.get(RPContextConstants.EVIL_CLIENT_CLIENTINFO);
		}
		return ci;
	}

	protected URI getClientUri() {
		URI uri;
		if (params.getBool(RPParameterConstants.FORCE_EVIL_CLIENT_URI)) {
			uri = opivCfg.getEvilRPUri();
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

	protected ClientID getClientID() {
		if (params.getBool(RPParameterConstants.FORCE_RANDOM_CLIENT_ID)) {
			return new ClientID();
		}
		if (params.getBool(RPParameterConstants.FORCE_EVIL_CLIENT_ID)) {
			return getEvilClientID();
		}
		if (Boolean.parseBoolean((String) stepCtx.get(RPParameterConstants.FORCE_CLIENT_REGISTRATION))) {
			// TODO: could cause conflicts as registration is enforced per testStep, not per RP
			return clientInfo.getID();
		}
		return supplyHonestOrEvil(this::getHonestClientID, this::getEvilClientID);
	}

	protected ClientID getHonestClientID() {
		return getStoredClientInfo(true).getID();
	}

	protected ClientID getEvilClientID() {
		return getStoredClientInfo(false).getID();
	}

	protected Secret getHonesClientSecret() {
		return getStoredClientInfo(true).getSecret();
	}

	protected Secret getEvilClientSecret() {
		return getStoredClientInfo(false).getSecret();
	}

	protected Secret getClientSecret() {
		if (params.getBool(RPParameterConstants.FORCE_EVIL_CLIENT_SECRET)) {
			return getEvilClientSecret();
		}
		if (params.getBool(RPParameterConstants.FORCE_RANDOM_CLIENT_SECRET)) {
			return new Secret(32);
		}
		if (Boolean.parseBoolean((String) stepCtx.get(RPParameterConstants.FORCE_CLIENT_REGISTRATION))) {
			// TODO: conflict, if two RPs are registered?
			return clientInfo.getSecret();
		}
		return supplyHonestOrEvil(this::getHonesClientSecret, this::getEvilClientSecret);
	}

	private String getRegistrationAccessToken() {
		String registrationToken = supplyHonestOrEvil(
				() -> testOPConfig.getAccessToken1(),
				() -> testOPConfig.getAccessToken2()
		);

		return registrationToken;
	}

	public boolean discoverOpIfNeeded() throws IOException, ParseException {
		boolean isLearningStep = Boolean.parseBoolean((String) stepCtx.get(RPContextConstants.IS_RP_LEARNING_STEP));
		// TODO: add and use startRPType instead of HONEST
		if(isLearningStep && type == RPType.HONEST) {
			return discoverRemoteOP();
		}
		if (opMetaData != null) {
			return true;
		}
		if (suiteCtx.get(RPContextConstants.DISCOVERED_OP_CONFIGURATION) != null) {
			opMetaData = (UnsafeOIDCProviderMetadata) suiteCtx.get(RPContextConstants.DISCOVERED_OP_CONFIGURATION);
			return true;
		}
		if (!Strings.isNullOrEmpty(testOPConfig.getOPMetadata())) {
			logger.log("Parsing OP Metadata from provided JSON string");
			opMetaData = UnsafeOIDCProviderMetadata.parse(testOPConfig.getOPMetadata());
			suiteCtx.put(RPContextConstants.DISCOVERED_OP_CONFIGURATION, opMetaData);
			return true;
		}
		return false;
	}

	private boolean discoverRemoteOP() throws IOException, ParseException {
		logger.log("Running OP discovery");
		String targetOpUrlString = testOPConfig.getUrlOPTarget();
		if (Strings.isNullOrEmpty(targetOpUrlString)) {
			targetOpUrlString = opMetaData.getIssuer().getValue();
		}
		Issuer issuer = new Issuer(targetOpUrlString);
		OIDCProviderConfigurationRequest request = new OIDCProviderConfigurationRequest(issuer);

		HTTPRequest httpRequest = request.toHTTPRequest();
		try {
			HTTPResponse httpResponse = httpRequest.send();

			if (!httpResponse.indicatesSuccess()) {
				logger.log("OP Discovery failed");
				logger.logHttpResponse(httpResponse, httpResponse.getContent());
				return false;
			}
			logger.log("OP Configuration received");
			logger.logHttpResponse(httpResponse, httpResponse.getContent());

			UnsafeOIDCProviderMetadata opMetadata = UnsafeOIDCProviderMetadata.parse(httpResponse.getContentAsJSONObject());
			opMetaData = opMetadata;
			suiteCtx.put(RPContextConstants.DISCOVERED_OP_CONFIGURATION, opMetadata);
			return true;
		} catch (ConnectException e) {
			logger.log("Connection exception", e);
			return false;
		}
	}

	private boolean areStepRequirementsMet() {
		// check if RP Parameters provided in TestPlan contradict with user provided configuration
		if (Boolean.parseBoolean((String) stepCtx.get(RPParameterConstants.FORCE_CLIENT_REGISTRATION))) {
			if (opMetaData == null) {
				opMetaData = (UnsafeOIDCProviderMetadata) suiteCtx.get(RPContextConstants.DISCOVERED_OP_CONFIGURATION);
			}
			if (opMetaData == null || opMetaData.getRegistrationEndpointURI() == null) {
				logger.log("TestStep requires dynamic registration but no registration endpoint was found");
				return false;
			}
		}
		// TODO: add additional requirement checks as needed

		return true;
	}



	// send a response to dangling browser
	protected void sendCallbackResponse(HttpServletResponse resp, HttpServletRequest req) throws IOException, ParseException {

		// extract scheme, authority, path
		String url = UriBuilder.fromUri(req.getRequestURI())
				.host(req.getServerName())
				.scheme(req.getScheme())
				.replaceQuery(null)
				.fragment(null)
				.build().toString();

		HTTPResponse httpRes = null;
		StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html>");
		sb.append("<html>");
		sb.append("<head><title>PrOfESSOS Callback</title></head>");
		sb.append("<body>");
		sb.append("<h2>PrOfESSOS OP Verifier</h2>");
		sb.append("<strong>Callback confirmation</strong>");
		sb.append("<div><p>Received callback at <code>");
		sb.append(url); // TODO: HTML encode
		sb.append("</code></p></div>");
		sb.append("</body>");
		sb.append("</html>");

		httpRes = new HTTPResponse(HTTPResponse.SC_OK);
		httpRes.setContentType("text/html; charset=UTF-8");
		httpRes.setHeader("Cache-Control", "no-cache, no-store");
		httpRes.setHeader("Pragma", "no-cache");
		httpRes.setContent(sb.toString());

		ServletUtils.applyHTTPResponse(httpRes, resp);
		resp.flushBuffer();
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

}
