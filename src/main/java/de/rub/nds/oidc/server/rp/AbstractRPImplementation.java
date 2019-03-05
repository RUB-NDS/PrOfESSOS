package de.rub.nds.oidc.server.rp;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.client.ClientInformation;
import com.nimbusds.oauth2.sdk.client.ClientRegistrationResponse;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallenge;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.util.JSONObjectUtils;
import com.nimbusds.openid.connect.sdk.*;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderConfigurationRequest;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.rp.*;
import de.rub.nds.oidc.log.TestStepLogger;
import de.rub.nds.oidc.server.OPIVConfig;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.test_model.ParameterType;
import de.rub.nds.oidc.test_model.RPConfigType;
import de.rub.nds.oidc.test_model.TestOPConfigType;
import de.rub.nds.oidc.utils.InstanceParameters;
import net.minidev.json.JSONObject;
import org.apache.commons.lang.RandomStringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
		return type == RPType.HONEST ? getHonestRedirectUri() : getEvilRedirectUri();
	}

	protected URI getEvilRedirectUri() {
		return UriBuilder.fromUri(opivCfg.getEvilRPUri()).path(testId).path(REDIRECT_PATH).build();
	}

	protected URI getHonestRedirectUri() {
		return UriBuilder.fromUri(opivCfg.getHonestRPUri()).path(testId).path(REDIRECT_PATH).build();
	}


	protected URI manipulateURI(URI uri, boolean addSubdomain, boolean addPath) {
		String rnd = RandomStringUtils.randomAlphanumeric(12);

		UriBuilder ub = UriBuilder.fromUri(uri);
		if (addSubdomain) {
			ub.host(rnd + "." + uri.getHost());
		}
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
	public void runTestStepSetup() throws ParseException, IOException {
		boolean success = true;

		success &= areStepRequirementsMet();

		// dont run setup steps for RP2 unless neccessary
		if (
			RPType.HONEST.equals(type)
			|| !Boolean.valueOf((String) stepCtx.get(RPParameterConstants.IS_SINGLE_RP_TEST))
			|| Boolean.valueOf((String) stepCtx.get(RPParameterConstants.FORCE_CLIENT_REGISTRATION))
		) {
			success &= discoverOpIfNeeded();
			success &= evalConfig();
			success &= registerClientIfNeeded();
			if (success) {
				prepareAuthnReq();
			}
		}
		if (RPType.EVIL.equals(type)) {
			stepCtx.put(RPContextConstants.STEP_SETUP_FINISHED, success);
		}
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
		ab.idTokenHint(getIdTokenHint());
		ab.endpointURI(opMetaData.getAuthorizationEndpointURI());

		AuthenticationRequest authnReq = ab.build();

		// make prepared request available for the browser
		String currentRP = type == RPType.HONEST ? RPContextConstants.RP1_PREPARED_AUTHNREQ
				: RPContextConstants.RP2_PREPARED_AUTHNREQ;
		stepCtx.put(currentRP, authnReq.toURI());
	}

	protected abstract URI getAuthReqRedirectUri();
	protected abstract URI getTokenReqRedirectUri();
	protected abstract ResponseType getAuthReqResponseType();
	protected abstract Scope getAuthReqScope();
	protected abstract ClientID getAuthReqClientID();
	
	@Nullable protected abstract State getAuthReqState();
	@Nullable protected abstract Nonce getAuthReqNonce();
	@Nullable protected abstract ResponseMode getAuthReqResponseMode();
	@Nullable protected abstract Prompt getAuthReqPrompt();
	@Nullable protected abstract ClaimsRequest getAuthReqClaims();

	@Nullable protected abstract CodeChallenge getCodeChallenge();
	@Nullable protected abstract JWT getIdTokenHint();

	@Nullable protected abstract ClientAuthenticationMethod getRegistrationClientAuthMethod();
	protected abstract HashSet<ResponseType> getRegistrationResponseTypes();
	protected abstract HashSet<GrantType> getRegistrationGrantTypes();


	private boolean registerClientIfNeeded() throws IOException, ParseException {
		if (Boolean.valueOf((String) stepCtx.get(RPParameterConstants.FORCE_CLIENT_REGISTRATION))) {
			// don't store new clientConfig in suiteCtx
			return registerClient(type);
		}
		OIDCClientInformation ci = getStoredClientInfo(type == RPType.HONEST);
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
//				getRegistrationEndpoint(),
				opMetaData.getRegistrationEndpointURI(),
				clientMetadata,
				bearerAccessToken
		);

		HTTPResponse response = regRequest.toHTTPRequest().send();
		logger.log("Registration request prepared");
		logger.logHttpRequest(regRequest.toHTTPRequest(), regRequest.toHTTPRequest().getQueryAsJSONObject().toString());

		ClientRegistrationResponse regResponse = OIDCClientRegistrationResponseParser.parse(response);

		if (!regResponse.indicatesSuccess()) {
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

		stepCtx.put(RPContextConstants.CLIENT_REGISTRATION_FAILED, false);

		return true;
	}


	private boolean evalConfig() {
		// attempt to parse user provided ClientConfig JSON strings
		String config = type.equals(RPType.HONEST) ? testOPConfig.getClient1Config() : testOPConfig.getClient2Config();
		if (Strings.isNullOrEmpty(config)) {
//			logger.log("Client Config not provided");
			// not an error, as long as dynamic registration works
			return true;
		}
		try {
//			Object jsonConfig = new JSONParser(JSONParser.MODE_PERMISSIVE).parse(config);
			JSONObject jsonConfig = JSONObjectUtils.parse(config);
			OIDCClientInformation clientInfo = (OIDCClientInformation) ClientInformation.parse(jsonConfig);
			setClientInfo(clientInfo);
			storeClientInfo();
			return true;
		} catch (com.nimbusds.oauth2.sdk.ParseException e) {
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
		if(params.getBool(RPParameterConstants.FORCE_EVIL_CLIENT_ID)) {
			return getEvilClientID();
		}
		if (Boolean.valueOf((String) stepCtx.get(RPParameterConstants.FORCE_CLIENT_REGISTRATION))) {
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
		if (Boolean.valueOf((String) stepCtx.get(RPParameterConstants.FORCE_CLIENT_REGISTRATION))) {
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

		if (!Strings.isNullOrEmpty(testOPConfig.getOPMetadata()) && opMetaData == null) {
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
//		logger.log("OP Configuration already retrieved, discovery not required");
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
		if (Boolean.valueOf((String) stepCtx.get(RPParameterConstants.FORCE_CLIENT_REGISTRATION))) {
			if (opMetaData == null) {
				opMetaData = (OIDCProviderMetadata) suiteCtx.get(RPContextConstants.DISCOVERED_OP_CONFIGURATION);
			}
			if (opMetaData == null || opMetaData.getRegistrationEndpointURI() == null) {
				logger.log("TestStep requires dynamic registration but no registration endpoint was found");
				return false;
			}
		}
		// TODO: add additional requirement checks as needed
		
		return true;
	}


	@Nonnull
	protected AuthenticationResponse processCallback(HttpServletRequest req, HttpServletResponse resp, @Nullable RequestPath path) throws IOException, URISyntaxException, ParseException {
		HTTPRequest httpRequest = ServletUtils.createHTTPRequest(req);
//		logger.log("Callback received");
//		logger.logHttpRequest(req, httpRequest.getQuery());

		CompletableFuture waitForBrowser = (CompletableFuture) stepCtx.get(RPContextConstants.BLOCK_RP_FOR_BROWSER_FUTURE);


		// send default response to hanging browser
		sendCallbackResponse(resp, req);
		// wait for browser confirmation ...
		try {
			// TODO: is this always necessary or only if we need the browser later on?
			waitForBrowser.get(5, TimeUnit.SECONDS);
		} catch (TimeoutException | ExecutionException | InterruptedException e) {
			logger.log("Timeout waiting for browser redirect URL", e);
		}
		// ...and extract redirect URI from browser (fragment includes tokens and or error messages in implicit flows)
		URI callbackUri;
		String lastUrl = (String) stepCtx.get(RPContextConstants.LAST_BROWSER_URL);
		callbackUri = new URI(lastUrl);
		logger.log("Redirect URL as seen in Browser: " + lastUrl);

		
		
		// parse received authentication response
		AuthenticationResponse authnResp;
		try {
			// handles query, fragment and form_post response_modes
			authnResp = AuthenticationSuccessResponse.parse(httpRequest);
			return authnResp;
		} catch (ParseException e) {
			// but fails on error response
		}
		try {
			authnResp = AuthenticationErrorResponse.parse(httpRequest);
			
			String user = (String) stepCtx.get(RPContextConstants.CURRENT_USER_USERNAME);
			String pass = (String) stepCtx.get(RPContextConstants.CURRENT_USER_USERNAME);
			String opAuthEndp = opMetaData.getAuthorizationEndpointURI().toString();

			logger.log(String.format("Authentication at %s as %s with password %s failed:", opAuthEndp, user, pass));
			logger.logHttpRequest(httpRequest, httpRequest.getQuery());
			
			ErrorObject error = authnResp.toErrorResponse().getErrorObject();
			logger.log("Error received: " + error.getDescription());
			logger.log(error.toJSONObject().toString());
			
			return authnResp;
		} catch (ParseException e) {
			logger.log("Invalid authentication response received");
			logger.logHttpRequest(httpRequest, httpRequest.getQuery());

			return new AuthenticationErrorResponse(callbackUri, 
					new ErrorObject("ParseException","Failed to parse authentication response"),
					null, null
			);
		}
	}


	@Nonnull
	protected TokenResponse redeemAuthCode(AuthorizationCode code) throws IOException, ParseException {
		
		URI redirectURI = getTokenReqRedirectUri();
		AuthorizationCodeGrant codeGrant = new AuthorizationCodeGrant(code, redirectURI);

		TokenRequest request = new TokenRequest(
				opMetaData.getTokenEndpointURI(),
				// temporarily set basic auth to construct the request 
				new ClientSecretBasic(clientInfo.getID(), clientInfo.getSecret()),
				codeGrant);
		
		HTTPRequest httpRequest = request.toHTTPRequest();
		// preform request customization as per testplan
		tokenRequestApplyClientAuth(httpRequest);

		logger.log("Token request prepared.");
		logger.logHttpRequest(httpRequest, httpRequest.getQuery());

		TokenResponse response = OIDCTokenResponseParser.parse(request.toHTTPRequest().send());

		if (!response.indicatesSuccess()) {
			TokenErrorResponse errorResponse = response.toErrorResponse();
			logger.log("Code redemption failed");
			logger.logHttpResponse(errorResponse.toHTTPResponse(), errorResponse.toHTTPResponse().getContent());
//			stepCtx.put(RPContextConstants.RP_INDICATED_STEP_RESULT, TestStepResult.FAIL);
			return response.toErrorResponse();
		}

		OIDCTokenResponse tokenSuccessResponse = (OIDCTokenResponse) response.toSuccessResponse();
		logger.log("Code redeemed for Token:");
		logger.logHttpResponse(response.toHTTPResponse(), response.toHTTPResponse().getContent());

//		JWT idToken = tokenSuccessResponse.getOIDCTokens().getIDToken();
//		AccessToken accessToken = tokenSuccessResponse.getOIDCTokens().getAccessToken();
//		RefreshToken refreshToken = tokenSuccessResponse.getOIDCTokens().getRefreshToken();
		return tokenSuccessResponse;
	}

	protected void tokenRequestApplyClientAuth(HTTPRequest req) {
		// remove temporary Authorization header
		req.setHeader("Authorization", null);
		
		try {
			String encodedID = URLEncoder.encode(getClientID().getValue(), "UTF-8");
			String encodedSecret = URLEncoder.encode(getClientSecret().getValue(), "UTF-8");
			
			StringBuilder sb = new StringBuilder();
			
			// client_secret_post
			if (params.getBool(RPParameterConstants.TOKENREQ_FORCE_CLIENTAUTH_POST)) {
				String encodedQuery = req.getQuery();
				sb.append(encodedQuery);
				if (!params.getBool(RPParameterConstants.TOKENREQ_CLIENTAUTH_EMPTY_ID)) {
					sb.append("&client_id=");
					sb.append(encodedID);
				}
				if (!params.getBool(RPParameterConstants.TOKENREQ_CLIENTAUTH_EMPTY_SECRET)) {
					sb.append("&client_secret=");
					sb.append(encodedSecret);
				}
				req.setQuery(sb.toString());
				return;
			}
	
			// client_secret_basic
			sb.append("Basic ");
			StringBuilder credentials = new StringBuilder();
			if (!params.getBool(RPParameterConstants.TOKENREQ_CLIENTAUTH_EMPTY_ID)) {
				credentials.append(getClientID().getValue());
			}
			credentials.append(":");
			if (!params.getBool(RPParameterConstants.TOKENREQ_CLIENTAUTH_EMPTY_SECRET)) {
				credentials.append(getClientSecret().getValue());
			}
	
			String b64Creds = Base64.getEncoder().encodeToString(credentials.toString().getBytes(Charset.forName("UTF-8")));
			sb.append(b64Creds);
			
			req.setHeader("Authorization", sb.toString());
		} catch (UnsupportedEncodingException e) {
			// utf-8 should be supported ?
			logger.log("Could not encode client credentials for token request.");
		}
	}

	@Nonnull
	protected UserInfoResponse requestUserInfo(AccessToken at) throws IOException, ParseException {
		BearerAccessToken bat = (BearerAccessToken) at;

		HTTPResponse httpResponse = new UserInfoRequest(opMetaData.getUserInfoEndpointURI(), bat)
				.toHTTPRequest()
				.send();
		logger.log("UserInfo requested");
		logger.logHttpResponse(httpResponse, httpResponse.getContent());

		UserInfoResponse userInfoResponse = UserInfoResponse.parse(httpResponse);
//		if (!userInfoResponse.indicatesSuccess()) {
//			return null;
//		}
//
//		UserInfo userInfo = userInfoResponse.toSuccessResponse().getUserInfo();
		return userInfoResponse;
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
