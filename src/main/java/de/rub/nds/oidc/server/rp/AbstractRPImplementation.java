/****************************************************************************
 * Copyright 2019 Ruhr-Universität Bochum.
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

package de.rub.nds.oidc.server.rp;

import com.google.common.base.Strings;
import com.nimbusds.jose.jwk.JWKSet;
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
import com.nimbusds.openid.connect.sdk.rp.*;
import de.rub.nds.oidc.log.TestStepLogger;
import de.rub.nds.oidc.server.InvalidConfigurationException;
import de.rub.nds.oidc.server.OPIVConfig;
import de.rub.nds.oidc.server.TestNotApplicableException;
import de.rub.nds.oidc.server.TestStepParameterConstants;
import de.rub.nds.oidc.server.op.OPType;
import de.rub.nds.oidc.test_model.ParameterType;
import de.rub.nds.oidc.test_model.RPConfigType;
import de.rub.nds.oidc.test_model.TestOPConfigType;
import de.rub.nds.oidc.utils.InstanceParameters;
import de.rub.nds.oidc.utils.LogUtils;
import de.rub.nds.oidc.utils.UnsafeOIDCProviderMetadata;
import de.rub.nds.oidc.utils.UnsafeTLSHelper;
import net.minidev.json.JSONObject;
import org.apache.commons.lang3.RandomStringUtils;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.util.*;
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
	protected UnsafeTLSHelper tlsHelper;

	public AbstractRPImplementation() {
	}

	@Override
	public void setRPConfig(RPConfigType cfg) {
		this.cfg = cfg;
	}

	@Override
	public void setOPIVConfig(OPIVConfig opivCfg) {
		this.opivCfg = opivCfg;
		initTlsHelper();
	}

	private void initTlsHelper() {
		this.tlsHelper = new UnsafeTLSHelper(opivCfg);
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
	public RPType getRPType() {
		return type;
	}

	@Override
	public void setContext(Map<String, Object> suiteCtx, Map<String, Object> stepCtx) {
		this.suiteCtx = suiteCtx;
		this.stepCtx = stepCtx;
		restoreOpMetaDataFromSuiteCtx();
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

	protected URI getJwkSetURI() {
		return type == RPType.HONEST ? getHonestJwkSetUri() : getEvilJwkSetUri();
	}

	protected URI getEvilJwkSetUri() {
		return UriBuilder.fromUri(opivCfg.getEvilRPUri()).path(testId).path(JWKS_PATH).build();
	}

	protected URI getHonestJwkSetUri() {
		return UriBuilder.fromUri(opivCfg.getHonestRPUri()).path(testId).path(JWKS_PATH).build();
	}

	protected boolean isStartRP() {
		String startRP = (String) stepCtx.getOrDefault(RPContextConstants.START_RP_TYPE, "HONEST");
		return type.toString().equals(startRP);
	}

	private void restoreOpMetaDataFromSuiteCtx() {
		Object storedOpMetaData = suiteCtx.get(RPContextConstants.DISCOVERED_OP_CONFIGURATION);
		if (storedOpMetaData != null) {
			this.opMetaData = (UnsafeOIDCProviderMetadata) storedOpMetaData;
		}
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
			newHost = newHost + "." + RPContextConstants.INVALID_TLD;
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
	public void runTestStepSetup() throws ParseException, IOException, InvalidConfigurationException, TestNotApplicableException {
		boolean currentIsStartRp = isStartRP();

		// dont run setup steps for RP2 unless neccessary
		if (currentIsStartRp) {
			validateProvidedOpConfig(testOPConfig);
		}
		if (currentIsStartRp || !Boolean.parseBoolean((String) stepCtx.get(RPParameterConstants.IS_SINGLE_RP_TEST))) {
			discoverOpIfNeeded();
			registerClientIfNeeded();
			prepareAuthnReq();
		}

		if (!currentIsStartRp) {
			// check if prerequisites are fulfilled and test step can be run
			stepCtx.put(RPContextConstants.RP1_PREPARED_REDIRECT_URI, getHonestRedirectUri());
			stepCtx.put(RPContextConstants.RP2_PREPARED_REDIRECT_URI, getEvilRedirectUri());
			// let the browser know that we are ready to go
			stepCtx.put(RPContextConstants.STEP_SETUP_FINISHED, true);
		}
	}

	private void validateProvidedOpConfig(TestOPConfigType cfg) throws InvalidConfigurationException {
		boolean areMinRequiredFieldsSet = true;
		areMinRequiredFieldsSet &= !Strings.isNullOrEmpty(cfg.getUser1Name());
		areMinRequiredFieldsSet &= !Strings.isNullOrEmpty(cfg.getUser2Name());
		areMinRequiredFieldsSet &= !Strings.isNullOrEmpty(cfg.getUser1Pass());
		areMinRequiredFieldsSet &= !Strings.isNullOrEmpty(cfg.getUser2Pass());
		areMinRequiredFieldsSet &= !(Strings.isNullOrEmpty(cfg.getUrlOPTarget()) && Strings.isNullOrEmpty(cfg.getOPMetadata()));

		if (!areMinRequiredFieldsSet) {
			throw new InvalidConfigurationException("Missing required OPMetadata configuration parameters");
		}
	}

	@Override
	public void prepareAuthnReq() {
		ResponseType rt = getAuthReqResponseType();
		Scope scope = getAuthReqScope();
		ClientID clientID = getAuthReqClientID();
		URI redirectURI = getAuthReqRedirectUri();

		URI authReqUriTmpl;
		if (scope.contains("openid")) {
			// OIDC
			AuthenticationRequest.Builder ab = new AuthenticationRequest.Builder(rt, scope, clientID, redirectURI);
			ab.prompt(getAuthReqPrompt());
			ab.claims(getAuthReqClaims());
			ab.nonce(getAuthReqNonce());
			ab.state(getAuthReqState());
			ab.responseMode(getAuthReqResponseMode());
			ab.endpointURI(opMetaData.getAuthorizationEndpointURI());
			authReqUriTmpl = ab.build().toURI();
		} else {
			// OAUTH 
			AuthorizationRequest.Builder ab = new AuthorizationRequest.Builder(rt, clientID);
			ab.redirectionURI(redirectURI);
			ab.state(getAuthReqState());
			ab.responseMode(getAuthReqResponseMode());
			ab.endpointURI(opMetaData.getAuthorizationEndpointURI());
			ab.state(getAuthReqState());
			ab.responseMode(getAuthReqResponseMode());
			ab.endpointURI(opMetaData.getAuthorizationEndpointURI());
			authReqUriTmpl = ab.build().toURI();
		}


		URI authnReqUri = applyPkceParamstoAuthReqUri(authReqUriTmpl);
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
	protected abstract ClientAuthenticationMethod getRegistrationClientAuthMethod();

	@Nullable
	protected JWKSet getRegistrationJwkSet() {
		return null;
	}

	@Nullable
	protected URI getRegistrationJwkSetURI() {
		return null;
	}

	protected abstract HashSet<ResponseType> getRegistrationResponseTypes();

	protected abstract HashSet<GrantType> getRegistrationGrantTypes();


	private void registerClientIfNeeded() throws IOException, ParseException, InvalidConfigurationException {
		// make sure OpMetadata is loaded
		restoreOpMetaDataFromSuiteCtx();

		if (Boolean.parseBoolean((String) stepCtx.get(RPParameterConstants.FORCE_CLIENT_REGISTRATION))) {
			// run registration for current testStep but do not store new clientConfig in suiteCtx
			registerClient(type);
			return;
		}

		// default: use clientInfo from suiteCtx (from previous test, user provided config, or earlier registration)
		try {
			loadClientConfigIfNeeded();
			// check if test step is applicable to loaded client configuration
			boolean isRunnable = isTestStepRunnableForConfig(clientInfo);
			if (!isRunnable) {
				// otherwise, try to run dynamic registration for current test step requirements
				registerClient(type);
				storeClientInfo();
			}
		} catch (InvalidConfigurationException ex) {
			// user provided config empty or invalid
			registerClient(type);
			// store valid clientInfo in suiteCtx
			storeClientInfo();
		}
	}


	private void registerClient(RPType rpType) throws ParseException, IOException, InvalidConfigurationException {
		boolean isRegistrationSupported = opMetaData.getRegistrationEndpointURI() != null;
		if (!isRegistrationSupported) {
			logger.log("No RegistrationEndpoint found in OP metadata");
			throw new InvalidConfigurationException("Client Registration required but not supported.");
		}

		String client = getClientName();
		logger.log("Starting client registration for " + client);

		OIDCClientMetadata clientMetadata = new OIDCClientMetadata();

		Set<GrantType> grantTypes = getRegistrationGrantTypes();
		clientMetadata.setGrantTypes(grantTypes);

		clientMetadata.setRedirectionURI(getRedirectUri());
		clientMetadata.setName(getClientName());

		Set<ResponseType> responseType = getRegistrationResponseTypes();
		clientMetadata.setResponseTypes(responseType);

		ClientAuthenticationMethod tokenEndpointAuthMethod = getRegistrationClientAuthMethod();
		clientMetadata.setTokenEndpointAuthMethod(tokenEndpointAuthMethod);

		clientMetadata.setJWKSet(getRegistrationJwkSet());
		clientMetadata.setJWKSetURI(getRegistrationJwkSetURI());

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

		HTTPRequest regHttpRequest = regRequest.toHTTPRequest();
		LogUtils.addSenderHeader(regHttpRequest, getRPType());
		regHttpRequest.setHostnameVerifier(tlsHelper.getTrustAllHostnameVerifier());
		regHttpRequest.setSSLSocketFactory(tlsHelper.getTrustAllSocketFactory());
		HTTPResponse response = regHttpRequest.send();

		logger.log("Registration request prepared");
		logger.logHttpRequest(regRequest.toHTTPRequest(), regRequest.toHTTPRequest().getQueryAsJSONObject().toString());

		ClientRegistrationResponse regResponse = OIDCClientRegistrationResponseParser.parse(response);

		if (!regResponse.indicatesSuccess()) {
			logger.log("Dynamic Client Registration failed");
			logger.logHttpResponse(response, response.getContent());
			throw new InvalidConfigurationException("Client Registration attempt failed");
		}

		OIDCClientInformationResponse successResponse = (OIDCClientInformationResponse) regResponse;
		OIDCClientInformation clientInfo = successResponse.getOIDCClientInformation();

		setClientInfo(clientInfo);
		logger.log("Successfully registered client");
		logger.logHttpResponse(response, response.getContent());
	}

	private void loadClientConfigIfNeeded() throws InvalidConfigurationException {
		if (getClientInfo() != null) {
			// instance clientInfo already initialized
			return;
		}

		OIDCClientInformation ci;
		ci = getStoredClientInfo(type);
		if (ci == null) {
			ci = getUserProvidedClientInfo();
			setClientInfo(ci);
			storeClientInfo();
			return;
		}

		setClientInfo(ci);
	}

	protected OIDCClientInformation getClientInfo() {
		return clientInfo;
	}

	protected void setClientInfo(OIDCClientInformation info) {
		clientInfo = info;
	}

	private void storeClientInfo() {
		String clientProfileName = (String) stepCtx.get(TestStepParameterConstants.CLIENT_PROFILE_NAME);
		Map<String, Object> storageTarget = suiteCtx;

		if (!Strings.isNullOrEmpty(clientProfileName)) {
			HashMap<String, Object> store = (HashMap<String, Object>) suiteCtx.get(clientProfileName);
			if (store == null) {
				storageTarget = new HashMap<>();
			} else {
				storageTarget = store;
			}
		}

		if (type.equals(RPType.HONEST)) {
			storageTarget.put(RPContextConstants.HONEST_CLIENT_CLIENTINFO, clientInfo);
		} else {
			storageTarget.put(RPContextConstants.EVIL_CLIENT_CLIENTINFO, clientInfo);
		}

		// store in suite context
		if (!Strings.isNullOrEmpty(clientProfileName)) {
			suiteCtx.put(clientProfileName, storageTarget);
		}
	}

	@Nullable
	private OIDCClientInformation getStoredClientInfo(RPType type) {
		OIDCClientInformation ci;
		String clientProfileName = (String) stepCtx.get(TestStepParameterConstants.CLIENT_PROFILE_NAME);
		Map<String, Object> lookupContext = suiteCtx;

		if (!Strings.isNullOrEmpty(clientProfileName)) {
			HashMap<String, Object> store = (HashMap<String, Object>) suiteCtx.get(clientProfileName);
			if (store != null) {
				lookupContext = store;
			}
		}

		if (type.equals(RPType.HONEST)) {
			ci = (OIDCClientInformation) lookupContext.get(RPContextConstants.HONEST_CLIENT_CLIENTINFO);
		} else {
			ci = (OIDCClientInformation) lookupContext.get(RPContextConstants.EVIL_CLIENT_CLIENTINFO);
		}
		return ci;
	}

	private OIDCClientInformation getUserProvidedClientInfo() throws InvalidConfigurationException {
		// attempt to parse user provided ClientConfig JSON strings
		String config = type.equals(RPType.HONEST) ? testOPConfig.getClient1Config() : testOPConfig.getClient2Config();

		if (Strings.isNullOrEmpty(config)) {
			// no client configuration provided
			throw new InvalidConfigurationException("No Client Configuration provided.");
		}
		try {
			JSONObject jsonConfig = JSONObjectUtils.parse(config);
			OIDCClientInformation ci = OIDCClientInformation.parse(jsonConfig);
			return ci;
		} catch (com.nimbusds.oauth2.sdk.ParseException e) {
			logger.logCodeBlock("Failed to parse provided client metadata:", config);
			logger.logCodeBlock("Exception was: ", e.toString());
			throw new InvalidConfigurationException("Invalid Client Configuration provided");
		}
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
		return "PrOfESSOS Honest Test-Client";
	}

	protected String getEvilClientName() {
		return "PrOfESSOS Evil Test-Client";
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
		if (params.getBool(RPParameterConstants.FORCE_HONEST_CLIENT_ID)) {
			return getHonestClientID();
		}
		if (Boolean.parseBoolean((String) stepCtx.get(RPParameterConstants.FORCE_CLIENT_REGISTRATION))) {
			// TODO: could cause conflicts as registration is enforced per testStep, not per RP
			return clientInfo.getID();
		}
		return supplyHonestOrEvil(this::getHonestClientID, this::getEvilClientID);
	}

	protected ClientID getHonestClientID() {
		return getStoredClientInfo(RPType.HONEST).getID();
	}

	protected ClientID getEvilClientID() {
		return getStoredClientInfo(RPType.EVIL).getID();
	}

	protected Secret getHonesClientSecret() {
		return getStoredClientInfo(RPType.HONEST).getSecret();
	}

	protected Secret getEvilClientSecret() {
		return getStoredClientInfo(RPType.EVIL).getSecret();
	}

	protected Secret getClientSecret() {
		if (params.getBool(RPParameterConstants.FORCE_EVIL_CLIENT_SECRET)) {
			return getEvilClientSecret();
		}
		if (params.getBool(RPParameterConstants.FORCE_HONEST_CLIENT_SECRET)) {
			return getHonesClientSecret();
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

	public void discoverOpIfNeeded() throws IOException, ParseException, InvalidConfigurationException {
		if (opMetaData != null) {
			// OP configuration already retrieved earlier (other RP setup or former TestStep)
			return;
		}
		if (!isStartRP()) {
			// only run discovery once
			return;
		}

		boolean configValuesNotEmpty = !Strings.isNullOrEmpty(testOPConfig.getOPMetadata());
		boolean discoverySuccess;
		discoverySuccess = discoverRemoteOP();
		if (configValuesNotEmpty) {
			if (discoverySuccess) {
				// merge values provided in test config (response type, scopes, grants, that shall be used )
				// into discovery response.
				logger.log("Merging provided OPMetadata values with discovered OPConfiguration");

				JSONObject configMd = JSONObjectUtils.parse(testOPConfig.getOPMetadata());
				JSONObject discoMd = opMetaData.toJSONObject();
				configMd.forEach(discoMd::replace);

				opMetaData = UnsafeOIDCProviderMetadata.parse(configMd);
			} else {
				// discovery failed, use provided config
				logger.log("Parsing OP Metadata from provided JSON string");
				opMetaData = UnsafeOIDCProviderMetadata.parse(testOPConfig.getOPMetadata());
			}

		} else {
			if (!discoverySuccess) {
				throw new InvalidConfigurationException("Discovery failed and no valid OPMetadata configuration provided.");
			}
		}
		// store in suite context for retrieval in following test steps
		suiteCtx.put(RPContextConstants.DISCOVERED_OP_CONFIGURATION, opMetaData);
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
		LogUtils.addSenderHeader(httpRequest, getRPType());
		httpRequest.setSSLSocketFactory(tlsHelper.getTrustAllSocketFactory());
		httpRequest.setHostnameVerifier(tlsHelper.getTrustAllHostnameVerifier());
		try {
			HTTPResponse httpResponse = httpRequest.send();

			if (!httpResponse.indicatesSuccess()) {
				logger.log("OP Discovery failed");
				logger.logHttpResponse(httpResponse, httpResponse.getContent());
				return false;
			}
			logger.log("OP Configuration received");
			logger.logHttpResponse(httpResponse, httpResponse.getContent());

			UnsafeOIDCProviderMetadata mdResponse = UnsafeOIDCProviderMetadata.parse(httpResponse.getContentAsJSONObject());
			opMetaData = mdResponse;
			suiteCtx.put(RPContextConstants.DISCOVERED_OP_CONFIGURATION, mdResponse);
			return true;
		} catch (ConnectException e) {
			logger.log("Connection exception", e);
			return false;
		}
	}

	protected boolean isTestStepRunnableForConfig(OIDCClientInformation ci) {
		// TODO: add further checks or override in subclasses as necessary
		boolean check = RPConfigHelper.testRunnableForConfig(ci, params, stepCtx);
		return check;
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
		LogUtils.addSenderHeader(resp, getRPType());
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
