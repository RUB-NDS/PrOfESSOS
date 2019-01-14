package de.rub.nds.oidc.server.rp;

import com.google.common.base.Strings;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.client.ClientInformation;
import com.nimbusds.oauth2.sdk.client.ClientRegistrationResponse;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.util.JSONObjectUtils;
import com.nimbusds.openid.connect.sdk.*;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderConfigurationRequest;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.rp.*;
import de.rub.nds.oidc.log.TestStepLogger;
import de.rub.nds.oidc.server.OPIVConfig;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.test_model.ParameterType;
import de.rub.nds.oidc.test_model.RPConfigType;
import de.rub.nds.oidc.test_model.TestOPConfigType;
import de.rub.nds.oidc.test_model.TestStepResult;
import de.rub.nds.oidc.utils.InstanceParameters;
import net.minidev.json.JSONObject;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static de.rub.nds.oidc.server.rp.RPParameterConstants.FORCE_HONEST_CLIENT_URI;

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
				|| ! Boolean.valueOf((String) stepCtx.get(RPParameterConstants.IS_SINGLE_RP_TEST))
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
		ClaimsRequest claims = new ClaimsRequest();
//		claims.addIDTokenClaim("group");
		claims.addIDTokenClaim("profile");
		claims.addIDTokenClaim("name");
		claims.addIDTokenClaim("email");
		claims.addUserInfoClaim("email");
//		claims.addIDTokenClaim("given_name");
//		claims.addIDTokenClaim("middle_name");
		AuthenticationRequest authnReq = new AuthenticationRequest.Builder(
//			new ResponseType("code","token","id_token"), // TODO: check that these are covered by registered grant types
			new ResponseType("code", "id_token"), // TODO: check that these are covered by registered grant types
//			new ResponseType("code"),
			new Scope(OIDCScopeValue.OPENID, OIDCScopeValue.PROFILE, OIDCScopeValue.EMAIL),
			clientInfo.getID(),
			getRedirectUri())
			// .state(new State())
			.nonce(new Nonce())
			.claims(claims)
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
			grantTypes.add(GrantType.AUTHORIZATION_CODE);
//			grantTypes.add(GrantType.IMPLICIT);
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


	@Nullable
	protected AuthenticationSuccessResponse processCallback(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException, URISyntaxException, ParseException {
		// TODO: this will not parse form_post responses
		HTTPRequest httpRequest = ServletUtils.createHTTPRequest(req);
		logger.log("Callback received");
		logger.logHttpRequest(req, httpRequest.getQuery());

		CompletableFuture waitForBrowser = (CompletableFuture) stepCtx.get(RPContextConstants.BLOCK_RP_FOR_BROWSER_FUTURE);

		// send arbitrary default response to the waiting browser
		sendCallbackResponse(resp);
		// wait for browser confirmation to extract redirect_uri
		try {
			waitForBrowser.get(5, TimeUnit.SECONDS);
		} catch (TimeoutException | ExecutionException |InterruptedException e) {
			logger.log("Timeout waiting for browser redirect URL",e);
			stepCtx.put(RPContextConstants.RP_INDICATED_STEP_RESULT, TestStepResult.UNDETERMINED);
		}
		// retrieve actual redirect URI from browser - may include tokens and or error messages in implicit flows
		String lastUrl = (String) stepCtx.get(RPContextConstants.LAST_BROWSER_URL);
		logger.log("Redirect URL as seen in Browser");
		logger.log(lastUrl);
		URI callbackUri;
		callbackUri = new URI(lastUrl);

		String user = (String) stepCtx.get(RPContextConstants.CURRENT_USER_USERNAME);
		String pass = (String) stepCtx.get(RPContextConstants.CURRENT_USER_USERNAME);

		// parse the URL from Browser to include potential URI Fragments (implicit/hybrid flows)
		AuthenticationResponse authnResp = AuthenticationResponseParser.parse(callbackUri);
		if (authnResp instanceof AuthenticationErrorResponse) {
			String opAuthEndp = opMetaData.getAuthorizationEndpointURI().toString();
			logger.log(String.format("Authentication at %s as %s with password %s failed:", opAuthEndp, user, pass));
			logger.logHttpRequest(req, req.getQueryString());
			// store auth failed hint in context for browser retrieval
			stepCtx.put(RPContextConstants.RP_INDICATED_STEP_RESULT, TestStepResult.FAIL);
			return null;
		}

		return authnResp.toSuccessResponse();
	}


	@Nullable
	protected OIDCTokenResponse fetchToken(AuthenticationSuccessResponse successResponse) throws IOException, ParseException{
		AuthorizationCode code = successResponse.getAuthorizationCode();
		if (code == null) {
			logger.log("No authorization code received in code flow.");
			stepCtx.put(RPContextConstants.RP_INDICATED_STEP_RESULT, TestStepResult.FAIL);
			return null;
		}

		AuthorizationGrant codeGrant = new AuthorizationCodeGrant(code, getRedirectUri());

		TokenRequest request = new TokenRequest(
				opMetaData.getTokenEndpointURI(),
				new ClientSecretBasic(clientInfo.getID(), clientInfo.getSecret()),
				codeGrant);
		logger.log("Token request prepared.");
		logger.logHttpRequest(request.toHTTPRequest(), null);

		TokenResponse response = OIDCTokenResponseParser.parse(request.toHTTPRequest().send());

		if (!response.indicatesSuccess()) {
			TokenErrorResponse errorResponse = response.toErrorResponse();
			logger.log("Code redemption failed");
			logger.logHttpResponse(errorResponse.toHTTPResponse(), errorResponse.toHTTPResponse().getContent());
			stepCtx.put(RPContextConstants.RP_INDICATED_STEP_RESULT, TestStepResult.FAIL);
			return null;
		}

		OIDCTokenResponse tokenSuccessResponse = (OIDCTokenResponse) response.toSuccessResponse();
		logger.log("Code redeemed for Token:");
		logger.logHttpResponse(response.toHTTPResponse(), response.toHTTPResponse().getContent());

//		JWT idToken = tokenSuccessResponse.getOIDCTokens().getIDToken();
//		AccessToken accessToken = tokenSuccessResponse.getOIDCTokens().getAccessToken();
//		RefreshToken refreshToken = tokenSuccessResponse.getOIDCTokens().getRefreshToken();
		return tokenSuccessResponse;
	}

	@Nullable
	protected UserInfo requestUserInfo(AccessToken at) throws IOException, ParseException {
		BearerAccessToken bat = (BearerAccessToken) at;

		HTTPResponse httpResponse = new UserInfoRequest(opMetaData.getUserInfoEndpointURI(), bat)
				.toHTTPRequest()
				.send();
		logger.log("UserInfo requested");
		logger.logHttpResponse(httpResponse, httpResponse.getContent());

		UserInfoResponse userInfoResponse = UserInfoResponse.parse(httpResponse);
		if (! userInfoResponse.indicatesSuccess()) {
			return null;
		}

		UserInfo userInfo = userInfoResponse.toSuccessResponse().getUserInfo();
		return userInfo;
	}

	protected void sendCallbackResponse(HttpServletResponse resp) throws IOException, ParseException {

		// send some response to dangling browser
		HTTPResponse httpRes = null;
		StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html>");
		sb.append("<html>");
		sb.append("<head><title>PrOfESSOS Callback</title></head>");
		sb.append("<body>");
		sb.append("<h2>PrOfESSOS OP Verifier</h2>");
		sb.append("<strong>Callback confirmation</strong>");
		sb.append("<div><p>Received callback at <code>");
		sb.append(getRedirectUri().toString());
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
