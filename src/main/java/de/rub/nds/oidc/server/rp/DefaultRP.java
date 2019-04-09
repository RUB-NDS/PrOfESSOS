package de.rub.nds.oidc.server.rp;

import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.openid.connect.sdk.*;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.test_model.TestStepResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static de.rub.nds.oidc.server.rp.RPContextConstants.*;
import static de.rub.nds.oidc.server.rp.RPParameterConstants.*;

public class DefaultRP extends AbstractRPImplementation {


	@Override
	public void callback(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException, URISyntaxException, ParseException {

		HTTPRequest httpRequest = ServletUtils.createHTTPRequest(req);
		logger.log("Callback received");
		logger.logHttpRequest(req, httpRequest.getQuery());

		CompletableFuture<TestStepResult> browserBlocker = (CompletableFuture<TestStepResult>) stepCtx.get(BLOCK_BROWSER_AND_TEST_RESULT);

		AuthenticationResponse authnResp = processCallback(req, resp, path);

		if (!authnResp.indicatesSuccess()) {
			TestStepResult result = TestStepResult.PASS;
			if (params.getBool(AUTH_ERROR_FAILS_TEST) || Boolean.valueOf((String) stepCtx.get(IS_RP_LEARNING_STEP))) {
				result = TestStepResult.FAIL;
			}
			browserBlocker.complete(result);
			return;
		}


		ResponseType usedResponseType = (ResponseType) supplyHonestOrEvil(
				() -> stepCtx.get(RP1_AUTHNREQ_RT),
				() -> stepCtx.get(RP2_AUTHNREQ_RT)
		);

		// TODO: tokens should never ever be transmitted in query-string of URL, consider ignoring usedResponseType
		if (req.getQueryString() != null && req.getQueryString().matches(".*[&\\?](id|access)?_token=.*") &&
				(usedResponseType.impliesImplicitFlow() || usedResponseType.impliesHybridFlow())) {
			logger.log("Detected Token(s) in URL-QueryString (not URL-Fragment), assuming test failed.");
			browserBlocker.complete(TestStepResult.FAIL);
			return;
		}
		if (authnResp.toSuccessResponse().impliedResponseMode().equals(ResponseMode.FRAGMENT)
				&& !getRegistrationGrantTypes().contains(GrantType.IMPLICIT)) {
			logger.log("Implicit response detected but authorization_grant_type=implicit not registered by client.");
			logger.log("Assuming test failed.");
			browserBlocker.complete(TestStepResult.FAIL);
			return;
		}

		AccessToken at = null;
		JWT idToken = null;
		AuthenticationSuccessResponse successResponse = authnResp.toSuccessResponse();
		if (successResponse.impliedResponseType().impliesCodeFlow() && !params.getBool(FORCE_NO_REDEEM_AUTH_CODE)) {
			// attempt code redemption
			TokenResponse tokenResponse = redeemAuthCode(successResponse.getAuthorizationCode());
			if (!tokenResponse.indicatesSuccess()) {
				// code redemption failed, error messages have been logged already
				browserBlocker.complete(TestStepResult.PASS);
				return;
			}

			if (params.getBool(SUCCESSFUL_CODE_REDEMPTION_FAILS_TEST)) {
				logger.log("AuthorizationCode successfully redeemed, assuming test failed.");
				browserBlocker.complete(TestStepResult.FAIL);
				return;
			}

			OIDCTokens tokens = ((OIDCTokenResponse) tokenResponse).getOIDCTokens();
			at = tokens.getAccessToken();
			idToken = tokens.getIDToken();
		} else if (successResponse.impliedResponseType().impliesImplicitFlow()) {
			at = successResponse.getAccessToken();
			idToken = successResponse.getIDToken();
		}

		if (idToken != null) {
			if (Boolean.parseBoolean((String) stepCtx.get(IS_RP_LEARNING_STEP))) {
				// store a reference to a valid, untampered id token for later
				// comparison
				storeIdToken(idToken);
			}
			TestStepResult idTokenConditionResult = checkIdTokenCondition(idToken);
			if (idTokenConditionResult != null) {
				browserBlocker.complete(idTokenConditionResult);
				return;
			}
		}

		if (at != null) {
			UserInfoResponse userInfoResponse = requestUserInfo(at);
			if (userInfoResponse != null && userInfoResponse.indicatesSuccess()) {
				UserInfo ui = userInfoResponse.toSuccessResponse().getUserInfo();
				boolean match = checkUserInfo(ui, testOPConfig.getUser2Name());

				if (match && params.getBool(USER2_IN_USERINFO_FAILS_TEST)) {
					browserBlocker.complete(TestStepResult.FAIL);
					return;
				}
			}
		}

		// TODO: chekcIdToken and checkUserInfo should return TestStepResults and pick targetClaim and searchstring from instance variables

		// TODO: refreshToken handling

//		logger.log("release browser lock");
		browserBlocker.complete(TestStepResult.PASS);
		return;
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
			waitForBrowser.get(5, TimeUnit.SECONDS);
		} catch (TimeoutException | ExecutionException | InterruptedException e) {
			logger.log("Timeout waiting for browser redirect URL", e);
		}
		// ...and extract redirect URI from browser (fragment includes tokens and or error messages in implicit flows)
		URI callbackUri;
		String lastUrl = (String) stepCtx.get(RPContextConstants.LAST_BROWSER_URL);
		callbackUri = new URI(lastUrl);
//		logger.log("Redirect URL as seen in Browser: " + lastUrl);

		// parse received authentication response
		AuthenticationResponse authnResp;
		try {
			// handle query and fragment response_modes
			authnResp = AuthenticationResponseParser.parse(callbackUri);

		} catch (ParseException e) {
			// form_post response_modes
			// TODO: Untested
			try {
				authnResp = AuthenticationResponseParser.parse(getRedirectUri(), httpRequest.getQueryParameters());
			} catch (ParseException ex) {
				logger.log("Failed to parse Authentication Response.", ex);
				logger.log("Earlier exception was: ", e);

				logger.log("Invalid authentication response received");
				logger.logHttpRequest(httpRequest, httpRequest.getQuery());

				return new AuthenticationErrorResponse(callbackUri,
						new ErrorObject("ParseException", "Failed to parse authentication response"),
						null, null
				);
			}
		}

		if (authnResp.indicatesSuccess()) {
			AuthenticationSuccessResponse sr = authnResp.toSuccessResponse();
			StringBuilder sb = new StringBuilder();
			sr.toParameters().forEach((k, v) -> {
				sb.append(k + ": " + v + ", ");
			});
			sb.setLength(sb.length() - 2);
			logger.logCodeBlock(sb.toString(), "Authentication Success Response received:");
			return authnResp;
		}

		String user = (String) stepCtx.get(RPContextConstants.CURRENT_USER_USERNAME);
		String pass = (String) stepCtx.get(RPContextConstants.CURRENT_USER_USERNAME);
		String opAuthEndp = opMetaData.getAuthorizationEndpointURI().toString();

		logger.log(String.format("Authentication at %s as %s with password %s failed:", opAuthEndp, user, pass));
		logger.logHttpRequest(httpRequest, httpRequest.getQuery());

		ErrorObject error = authnResp.toErrorResponse().getErrorObject();
		logger.logCodeBlock(error.getDescription(), "Error received: ");
		logger.logCodeBlock(error.toJSONObject().toString(), null);

		return authnResp;
	}


	@Nonnull
	protected TokenResponse redeemAuthCode(AuthorizationCode code) throws IOException, ParseException {

		URI redirectURI = getTokenReqRedirectUri();
		AuthorizationCodeGrant codeGrant = new AuthorizationCodeGrant(code, redirectURI);

		TokenRequest request = new TokenRequest(
				opMetaData.getTokenEndpointURI(),
				// set Basic Authorization header to construct a temporary request
				// that is customized according to TestStepReference later on
				new ClientSecretBasic(clientInfo.getID(), clientInfo.getSecret()),
				codeGrant);

		HTTPRequest httpRequest = request.toHTTPRequest();
		// perform request customization as per TestStepReference
		tokenRequestApplyClientAuth(httpRequest);
		tokenRequestApplyPKCEParams(httpRequest);

		logger.log("Token request prepared.");
		logger.logHttpRequest(httpRequest, httpRequest.getQuery());

		TokenResponse response = OIDCTokenResponseParser.parse(httpRequest.send());

		if (!response.indicatesSuccess()) {
			TokenErrorResponse errorResponse = response.toErrorResponse();
			logger.log("Code redemption failed");
			logger.logHttpResponse(errorResponse.toHTTPResponse(), errorResponse.toHTTPResponse().getContent());
			return response.toErrorResponse();
		}

		OIDCTokenResponse tokenSuccessResponse = (OIDCTokenResponse) response.toSuccessResponse();
		logger.log("Code redeemed for Token:");
		logger.logHttpResponse(response.toHTTPResponse(), response.toHTTPResponse().getContent());

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
			// utf-8 should always be supported
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

		try {
			UserInfoResponse userInfoResponse = UserInfoResponse.parse(httpResponse);
			return userInfoResponse;
		} catch (ParseException e) {
			logger.log("Userinfo Request Failed");
			logger.log(e.getMessage());
			return new UserInfoErrorResponse(new ErrorObject("invalid response", "Invalid response upon userinfo request"));
		}
	}


	protected boolean checkUserInfo(UserInfo userInfo, String searchString) {
		// temporary: iterate first level keys of userinfo and compare w username
		boolean found = false;
		for (Map.Entry<String, Object> entry : userInfo.toJSONObject().entrySet()) {
			if (searchString.equals(entry.getValue())) {
				logger.log(String.format("String %s matches %s entry in received UserInfo", searchString, entry.getKey()));
				found = true;
			}
		}

		return found;
	}

	protected TestStepResult checkIdTokenCondition(JWT idToken) {
		return null;
	} 

	protected URI getAuthReqRedirectUri() {
		URI redirURI = params.getBool(AUTHNREQ_FORCE_EVIL_REDIRURI) ? getEvilRedirectUri() : getRedirectUri();

		boolean subdom = params.getBool(RPParameterConstants.AUTHNREQ_ADD_SUBDOMAIN_REDIRURI);
		boolean path = params.getBool(RPParameterConstants.AUTHNREQ_ADD_PATHSUFFIX_REDIRURI);
		boolean tld = params.getBool(RPParameterConstants.AUTHNREQ_ADD_INVALID_TLD);
		if (subdom || path || tld) {
			return manipulateURI(redirURI, subdom, path, tld);
		}

		return redirURI;
	}

	protected String getStoredAuthnReqString() {

		String currentRP = (type == RPType.HONEST) ? RPContextConstants.RP1_PREPARED_AUTHNREQ
				: RPContextConstants.RP2_PREPARED_AUTHNREQ;
		URI ar = (URI) stepCtx.get(currentRP);
		return ar.toString();
	}


	@Nullable
	protected URI getTokenReqRedirectUri() {
		if (params.getBool(TOKENREQ_FORCE_EVIL_REDIRURI)) {
			return getEvilRedirectUri();
		}
		if (params.getBool(TOKENREQ_REDIRURI_EXCLUDED)) {
			return null;
		}
		if (params.getBool(TOKENREQ_REDIRURI_ADD_SUBDOMAIN)) {
			return manipulateURI(getRedirectUri(), true, false, false);
		}
		if (params.getBool(TOKENREQ_REDIRURI_ADD_PATHSUFFIX)) {
			return manipulateURI(getRedirectUri(), false, true, false);
		}
		if (params.getBool(TOKENREQ_REDIRURI_ADD_TLD)) {
			return manipulateURI(getRedirectUri(), false, false, true);
		}
		
		return getRedirectUri();
	}

	protected ResponseType getAuthReqResponseType() {
		ResponseType rt = new ResponseType();

		if (params.getBool(AUTHNREQ_RESPONSE_TYPE_TOKEN)) {
			rt.add(ResponseType.Value.TOKEN);
		}
		if (params.getBool(AUTHNREQ_RESPONSE_TYPE_IDTOKEN)) {
			rt.add(OIDCResponseTypeValue.ID_TOKEN);
		}
		if (params.getBool(AUTHNREQ_RESPONSE_TYPE_CODE)) {
			rt.add(ResponseType.Value.CODE);
		}

		if (rt.isEmpty()) {
			// default
			rt.add(ResponseType.Value.CODE);
		}

		String key = (type == RPType.HONEST) ? RP1_AUTHNREQ_RT : RP2_AUTHNREQ_RT;
		stepCtx.put(key, rt);
		return rt;
	}

	protected Scope getAuthReqScope() {
		Scope scopes = new Scope(
				OIDCScopeValue.OPENID,
				OIDCScopeValue.PROFILE);
		return scopes;
	}

	protected ClientID getAuthReqClientID() {
		return getClientID();
	}

	@Nullable
	protected State getAuthReqState() {
		return new State();
	}

	@Nullable
	protected Nonce getAuthReqNonce() {
		return new Nonce();
	}

	@Nullable
	protected ResponseMode getAuthReqResponseMode() {

		if (params.getBool(AUTHNREQ_RESPONSE_MODE_FRAGMENT)) {
			return ResponseMode.FRAGMENT;
		}
		if (params.getBool(AUTHNREQ_RESPONSE_TYPE_FORM_POST)) {
			// TODO: check if a form_post response is succesfully reeceived
			return ResponseMode.FORM_POST;
		}

		return null;
	}

	@Nullable
	protected Prompt getAuthReqPrompt() {
		return null;
	}

	@Nullable
	protected ClaimsRequest getAuthReqClaims() {
		return null;
	}

	@Override
	@Nullable
	protected CodeChallengeMethod getCodeChallengeMethod() {
		CodeChallengeMethod cm = null;
		if (params.getBool(AUTHNREQ_PKCE_METHOD_PLAIN)) {
			cm = CodeChallengeMethod.PLAIN;
		}
		if (params.getBool(AUTHNREQ_PKCE_METHOD_S_256)) {
			cm = CodeChallengeMethod.S256;
		}

		return cm;
	}

	@Override
	@Nullable
	protected CodeVerifier getCodeChallengeVerifier() {
		CodeVerifier verifier = null;
		if (params.getBool(AUTHNREQ_PKCE_METHOD_PLAIN) || params.getBool(AUTHNREQ_PKCE_METHOD_S_256)
				|| params.getBool(AUTHNREQ_PKCE_METHOD_EXCLUDED)) {
			verifier = new CodeVerifier();
			// store for later
			logger.log("Storing generated CodeVerifier: " + verifier.getValue());
			stepCtx.put(RPContextConstants.STORED_PKCE_VERIFIER, verifier);
		}
		if (params.getBool(AUTHNREQ_PKCE_CHALLENGE_EXCLUDED)) {
			return null;
		}
		return verifier;
	}

	@Nullable
	protected JWT getIdTokenHint() {
		return null;
	}

	protected void storeIdToken(JWT idToken) {
		if (testOPConfig.getUser1Name().equals(stepCtx.get(CURRENT_USER_USERNAME))) {
			suiteCtx.put(STORED_USER1_IDTOKEN, idToken);
		} else {
			suiteCtx.put(STORED_USER2_IDTOKEN, idToken);
		}
	}

	@Override
	protected HashSet<GrantType> getRegistrationGrantTypes() {
		HashSet<GrantType> grantTypes = new HashSet<>();
		if (params.getBool(RPParameterConstants.REGISTER_GRANT_TYPE_AUTHCODE)) {
			grantTypes.add(new GrantType("authorization_code"));
		}
		if (params.getBool(RPParameterConstants.REGISTER_GRANT_TYPE_IMPLICIT)) {
			grantTypes.add(new GrantType("implicit"));
		}
		if (params.getBool(RPParameterConstants.REGISTER_GRANT_TYPE_REFRESH)) {
			grantTypes.add(new GrantType("refresh_token"));
		}
		if (grantTypes.isEmpty()) {
			//default
			grantTypes.add(new GrantType("authorization_code"));
		}

		return grantTypes;
	}

	@Override
	protected HashSet<ResponseType> getRegistrationResponseTypes() {
		HashSet<ResponseType> rts = new HashSet<>();

		if (params.getBool(REGISTER_RESPONSETYPE_CODE)) {
			rts.add(new ResponseType(ResponseType.Value.CODE));
		}
		if (params.getBool(REGISTER_RESPONSETYPE_TOKEN)) {
			rts.add(new ResponseType(ResponseType.Value.TOKEN));
		}
		if (params.getBool(REGISTER_RESPONSETYPE_IDTOKEN)) {
			rts.add(new ResponseType(OIDCResponseTypeValue.ID_TOKEN));
		}
		if (params.getBool(REGISTER_RESPONSETYPE_TOKEN_IDTOKEN)) {
			rts.add(new ResponseType(ResponseType.Value.TOKEN, OIDCResponseTypeValue.ID_TOKEN));
		}
		if (params.getBool(REGISTER_RESPONSETYPE_CODE_IDTOKEN)) {
			rts.add(new ResponseType(ResponseType.Value.CODE, OIDCResponseTypeValue.ID_TOKEN));
		}
		if (params.getBool(REGISTER_RESPONSETYPE_CODE_TOKEN)) {
			rts.add(new ResponseType(ResponseType.Value.CODE, ResponseType.Value.TOKEN));
		}
		if (params.getBool(REGISTER_RESPONSETYPE_CODE_TOKEN_IDTOKEN)) {
			rts.add(new ResponseType(ResponseType.Value.CODE, ResponseType.Value.TOKEN, OIDCResponseTypeValue.ID_TOKEN));
		}

		if (rts.isEmpty()) {
			// default to code flow
			rts.add(new ResponseType(ResponseType.Value.CODE));
		}

		return rts;
	}

	@Nullable
	@Override
	protected ClientAuthenticationMethod getRegistrationClientAuthMethod() {
		if (params.getBool(REGISTER_CLIENTAUTH_POST)) {
			return new ClientAuthenticationMethod("client_secret_post");
		}
		if (params.getBool(REGISTER_CLIENTAUTH_NONE)) {
			return new ClientAuthenticationMethod("client_secret_none");
		}

		// use Basic Auth per default (null):
		return null;
	}

	protected void tokenRequestApplyPKCEParams(HTTPRequest req) {
		CodeVerifier cv = getStoredPKCEVerifier();
		if (cv == null) {
			return;
		}
		String encodedQuery = req.getQuery();
		StringBuilder sb = new StringBuilder();
		sb.append(encodedQuery);
		sb.append("&code_verifier=");
		sb.append(cv.getValue());

		req.setQuery(sb.toString());
	}

	protected CodeVerifier getStoredPKCEVerifier() {
		return (CodeVerifier) stepCtx.get(RPContextConstants.STORED_PKCE_VERIFIER);
	}


}
