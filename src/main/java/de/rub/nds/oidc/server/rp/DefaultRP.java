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

import com.nimbusds.jose.jwk.JWKSet;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static de.rub.nds.oidc.server.rp.RPContextConstants.*;
import static de.rub.nds.oidc.server.rp.RPParameterConstants.*;
import de.rub.nds.oidc.utils.LogUtils;


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
		if (successResponse.getAuthorizationCode() != null && !params.getBool(FORCE_NO_REDEEM_AUTH_CODE)) {
			// attempt code redemption
			TokenResponse tokenResponse = redeemAuthCode(successResponse.getAuthorizationCode());
			if (!tokenResponse.indicatesSuccess()) {
				// code redemption failed, error messages have been logged already
				if (Boolean.parseBoolean((String) stepCtx.get(IS_RP_LEARNING_STEP))) {
					browserBlocker.complete(TestStepResult.UNDETERMINED);
				} else {
					browserBlocker.complete(TestStepResult.PASS);
				}
				return;
			}

			if (params.getBool(TOKEN_RECEIVAL_FAILS_TEST)) {
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
			TestStepResult result = checkUserInfo(userInfoResponse, null);
			if (result != null) {
				logger.log(String.format("Result returned from UserInfo condition check: %s", result.value()));
				browserBlocker.complete(result);
				return;
			}
		}

		browserBlocker.complete(TestStepResult.PASS);
		return;
	}

	@Override
	public void jwks(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		try {
			// TODO: read from config or keystore 
			//  currently these are never used for signing anything, only to host a pub key that could be used
			//  for key confusion (HMAC using pubkey)
			JWKSet evilJwkSet = JWKSet.parse("{\n" +
					"  \"keys\": [\n" +
					"    {\n" +
					"      \"kty\": \"RSA\",\n" +
					"      \"d\": \"S3S2tSnhSyJgxcbPe7cOQWy6oKmF5zNB4M5RuOwU6na6EHgsG4-5NK-R8oO9uvxgtjnIP3ChyvHMEV26MeWLZtPLvatPGj2cTtyfANdlYyFRiltwC-3Av94BMqFfd-PFis_nU4TKquAUrNrf1HsL0df8vSLyqHoXJbRsM71yuFzZCQqcVolJcIba3JyI3CuqXUz9wqFV0lieVCqJIqvBXqeqGKnS22aLGl65yPBGFIDWFSa3Y8EFMhAOBw9PwfumCC9lQzBI-xrjHdtCRLTPvzb6v3s4zGeiRsFcVDF5N_cThWuY3uXRgZCLLFxh6GxKLDj4pmZC4_IICD_KnEw4fQ\",\n" +
					"      \"e\": \"AQAB\",\n" +
					"      \"use\": \"sig\",\n" +
					"      \"kid\": \"Evil-Client-RSA\",\n" +
					"      \"alg\": \"RS256\",\n" +
					"      \"n\": \"jSxTW1XS6mba-GdY-0aIEXHTeUu5hlOR974-kUlDihJgcrnwsdKXnpoA76J02WuvP5ttdtw0u6wfn7hJ1vDB2KHLM5E2foK7z3NuuRsUI1VI3jTio2Ge-SDjM_Gaf3bv8rWnhbg0jvCZKHocZSM_8jrZofhTKdhXwXe3JxqRm5oxZaqhjhMFXzqoIgGRD0-KfSdmgaCyVu5uMIH7lZAWtdVQmRJAwuZ4RanOU0h2D0PKtv0g7Je7mCEoRD2c6XCuOXhaqyeqAljVqATsBm8ty6jWTubFNy5HjxGV7nHeQ_LGMNjWJh6zLL64RVOVYoCZ_PckBIAm5s8j6ZYWPnRFtQ\"\n" +
					"    }\n" +
					"  ]\n" +
					"}");


			JWKSet honestJwkSet = JWKSet.parse("{\n" +
					"  \"keys\": [\n" +
					"    {\n" +
					"      \"kty\": \"RSA\",\n" +
					"      \"d\": \"cPtR15jEDFRU6eHAGu_6M4qkHgNFYji6WeH2oNDHiXq50ftAsbsFXceNAGVDEEzFTp6st1qf_NrRfxbZwSTKeVLFuHL1BTUv5DbeGUPa3LZAfpFtEYgAbeLcn94mvPwd4S80KOJ5a77DD1ZhvhRSbJrjgAsgNAGc18ylIFi5x4LBh8m2EA1bE28f8j-DTxVLgT6waD5bbPgHStVMQ8eXUI7N9IfVvK34I--TVhAZvBhgtNRDnnT4KBlW7K_t-pW9sa64rKg12AG1ioXZ1mJDznlFYpYg30PeIcL7graGxDTX-UYwHf4IYdAPA9SLqhy57HzWJPGZVfSGbOc_bsbYWQ\",\n" +
					"      \"e\": \"AQAB\",\n" +
					"      \"use\": \"sig\",\n" +
					"      \"kid\": \"Honest-Client-RSA\",\n" +
					"      \"alg\": \"RS256\",\n" +
					"      \"n\": \"l6NTAcPFLtGxVFkCl-7qMsulEwx2w9XqLuRlDS1PH7WHgYf_pg4bylc7VhttRq5RJ0ndqR1ocSF4miL2bUVREskqxAROg-V6UaynThg-KQDrJh_U5lO0U5tFFNP29QQNSAI1fytz8r9Z_GEvF5enAVFLJJjgt7aF58lgSs_vj2rEF3lpK4kdle8Ao6KtuLdh7wSnu-e-SPi2a7b8PRISOx6_gAvdP4tUzGDGlP9rGqL4ZcZgCIpxhTkdf7X7gwppKfJy3WmW06lPc6ve03-RrIJAaUf75pJ7Niq-RB_HX41edF2n7OSxzbdrcv9m85a_jM4YZdQ7NLJPaaslhONlqQ\"\n" +
					"    }\n" +
					"  ]\n" +
					"}");

			resp.setHeader("Content-Type", "application/json");

			if (type == RPType.EVIL) {
				resp.getWriter().write(evilJwkSet.toPublicJWKSet().toString());
			} else {
				resp.getWriter().write(honestJwkSet.toPublicJWKSet().toString());
			}

			resp.flushBuffer();

		} catch (java.text.ParseException e) {
			logger.log("Could not read hardcoded JWK keystring in " + getClass().getName());
			throw new IOException(e);
		}
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
			logger.logCodeBlock("Authentication Success Response received:", sb.toString());
			return authnResp;
		}

		String user = (String) stepCtx.get(RPContextConstants.CURRENT_USER_USERNAME);
		String pass = (String) stepCtx.get(RPContextConstants.CURRENT_USER_USERNAME);
		String opAuthEndp = opMetaData.getAuthorizationEndpointURI().toString();

		logger.log(String.format("Authentication at %s as %s with password %s failed:", opAuthEndp, user, pass));
		logger.logHttpRequest(httpRequest, httpRequest.getQuery());

		ErrorObject error = authnResp.toErrorResponse().getErrorObject();
		logger.logCodeBlock("Error received: ", error.getDescription());
		logger.logCodeBlock(error.toJSONObject().toString());

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
		LogUtils.addSenderHeader(httpRequest, getRPType());
		httpRequest.setSSLSocketFactory(tlsHelper.getTrustAllSocketFactory());
		httpRequest.setHostnameVerifier(tlsHelper.getTrustAllHostnameVerifier());

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
		if (params.getBool(TOKENREQ_FORCE_NO_CLIENT_AUTH)) {
			return;
		}

		try {
			String encodedID = URLEncoder.encode(getClientID().getValue(), "UTF-8");
			String encodedSecret = URLEncoder.encode(getClientSecret().getValue(), "UTF-8");

			StringBuilder sb = new StringBuilder();

			// client_secret_post
			if (params.getBool(TOKENREQ_FORCE_CLIENTAUTH_POST)) {
				String encodedQuery = req.getQuery();
				sb.append(encodedQuery);
				if (!params.getBool(TOKENREQ_CLIENTAUTH_EXCL_ID)) {
					sb.append("&client_id=");
					if (!params.getBool(TOKENREQ_CLIENTAUTH_EMPTY_ID)) {
						sb.append(encodedID);
					}
				}
				if (!params.getBool(TOKENREQ_CLIENTAUTH_EXCL_SECRET)) {
					sb.append("&client_secret=");
					if (!params.getBool(TOKENREQ_CLIENTAUTH_EMPTY_SECRET)) {
						sb.append(encodedSecret);
					}
				}

				req.setQuery(sb.toString());
				return;
			}

			// client_secret_basic
			sb.append("Basic ");
			StringBuilder credentials = new StringBuilder();
			if (!params.getBool(TOKENREQ_CLIENTAUTH_EMPTY_ID)) {
				credentials.append(getClientID().getValue());
			}
			if (!params.getBool(TOKENREQ_CLIENTAUTH_EXCL_SECRET)) {
				credentials.append(":");
			}
			if (!params.getBool(TOKENREQ_CLIENTAUTH_EMPTY_SECRET) && !params.getBool(TOKENREQ_CLIENTAUTH_EXCL_SECRET)) {
				credentials.append(getClientSecret().getValue());
			}
			String b64Creds = Base64.getEncoder().encodeToString(credentials.toString().getBytes(Charset.forName("UTF-8")));

			if (params.getBool(TOKENREQ_CLIENTAUTH_EXCL_ID) && params.getBool(TOKENREQ_CLIENTAUTH_EXCL_SECRET)) {
				b64Creds = "";
			}

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

		HTTPRequest httpRequest = new UserInfoRequest(opMetaData.getUserInfoEndpointURI(), bat).toHTTPRequest();
		LogUtils.addSenderHeader(httpRequest, getRPType());
		httpRequest.setSSLSocketFactory(tlsHelper.getTrustAllSocketFactory());
		httpRequest.setHostnameVerifier(tlsHelper.getTrustAllHostnameVerifier());

		HTTPResponse httpResponse = httpRequest.send();
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

	protected TestStepResult checkUserInfo(UserInfoResponse userInfoResponse, @Nullable String searchString) {
		return null;
	}

	protected TestStepResult checkIdTokenCondition(JWT idToken) {
		return null;
	}

	protected URI getAuthReqRedirectUri() {
		URI redirURI = params.getBool(AUTHNREQ_FORCE_EVIL_REDIRURI) ? getEvilRedirectUri() : getRedirectUri();

		boolean subdom = params.getBool(AUTHNREQ_ADD_SUBDOMAIN_REDIRURI);
		boolean path = params.getBool(AUTHNREQ_ADD_PATHSUFFIX_REDIRURI);
		boolean tld = params.getBool(AUTHNREQ_ADD_INVALID_TLD);
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
		if (params.getBool(TOKENREQ_FORCE_HONEST_REDIRURI)) {
			return getHonestRedirectUri();
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
		Scope scopes;
		if (params.getBool(AUTHNREQ_ADD_INVALID_SCOPE)) {
			scopes = new Scope("openid", "and some","invalid","scopes","please!");
		} else {
		scopes = new Scope(
				OIDCScopeValue.OPENID,
				OIDCScopeValue.PROFILE);
		}
		
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
		if (params.getBool(AUTHNREQ_RESPONSE_MODE_QUERY)) {
			return ResponseMode.QUERY;
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
		if (params.getBool(REGISTER_GRANT_TYPE_AUTHCODE)) {
			grantTypes.add(new GrantType("authorization_code"));
		}
		if (params.getBool(REGISTER_GRANT_TYPE_IMPLICIT)) {
			grantTypes.add(new GrantType("implicit"));
		}
		if (params.getBool(REGISTER_GRANT_TYPE_REFRESH)) {
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
		if (params.getBool(REGISTER_CLIENTAUTH_JWT)) {
			return new ClientAuthenticationMethod("client_secret_jwt");
		}
		if (params.getBool(REGISTER_CLIENTAUTH_PK_JWT)) {
			return new ClientAuthenticationMethod("private_key_jwt");
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
