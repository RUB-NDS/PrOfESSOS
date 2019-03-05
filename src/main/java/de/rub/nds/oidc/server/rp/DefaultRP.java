package de.rub.nds.oidc.server.rp;

import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallenge;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.openid.connect.sdk.*;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.test_model.TestStepResult;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static de.rub.nds.oidc.server.rp.RPContextConstants.BLOCK_BROWSER_AND_TEST_RESULT;
import static de.rub.nds.oidc.server.rp.RPContextConstants.BLOCK_RP_FOR_BROWSER_FUTURE;
import static de.rub.nds.oidc.server.rp.RPParameterConstants.*;

public class DefaultRP extends AbstractRPImplementation {


	@Override
	public void callback(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException, URISyntaxException, ParseException {

		// TODO: this will not parse form_post responses
		HTTPRequest httpRequest = ServletUtils.createHTTPRequest(req);
		logger.log("Callback received");
		logger.logHttpRequest(req, httpRequest.getQuery());

		CompletableFuture waitForBrowser = (CompletableFuture) stepCtx.get(BLOCK_RP_FOR_BROWSER_FUTURE);
		CompletableFuture<TestStepResult> browserBlocker = (CompletableFuture<TestStepResult>) stepCtx.get(BLOCK_BROWSER_AND_TEST_RESULT);

		AuthenticationResponse authnResp = processCallback(req, resp, path);

		if (!authnResp.indicatesSuccess()) {
			TestStepResult result = params.getBool(AUTH_ERROR_FAILS_TEST) ? TestStepResult.FAIL : TestStepResult.PASS;
			browserBlocker.complete(result);
			return;
		}

		AccessToken at = null;
		JWT idToken = null;
		AuthenticationSuccessResponse successResponse = authnResp.toSuccessResponse();
		if (successResponse.impliedResponseType().impliesCodeFlow()) {
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
			boolean found = checkIdToken(idToken, testOPConfig.getUser2Name(), "sub");
			if (found && params.getBool(USER2_IN_IDTOKEN_SUB_FAILS_TEST)) {
				browserBlocker.complete(TestStepResult.FAIL);
				return;
			}
		}

		if (at != null) {
			UserInfoResponse userInfoResponse = requestUserInfo(at);
			if (userInfoResponse.indicatesSuccess()) {
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

	protected boolean checkIdToken(JWT idToken, String searchString, String targetClaim) {
		boolean found = false;
		try {
			found = idToken.getJWTClaimsSet().getClaim(targetClaim).equals(searchString);
		} catch (java.text.ParseException e) {
			logger.log("Failed to parse Claims from ID Token");
			logger.log(idToken.getParsedString());
		}
		return found;
	}

	protected URI getAuthReqRedirectUri() {
		URI redirURI = params.getBool(AUTHNREQ_FORCE_EVIL_REDIRURI) ? getEvilRedirectUri() : getRedirectUri();

		boolean subdom = params.getBool(RPParameterConstants.AUTHNREQ_ADD_SUBDOMAIN_REDIRURI);
		boolean path = params.getBool(RPParameterConstants.AUTHNREQ_ADD_PATHSUFFIX_REDIRURI);
		if (subdom || path) {
			return manipulateURI(redirURI, subdom, path);
		}

		return redirURI;
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
			return manipulateURI(getRedirectUri(), true, false);
		}
		if (params.getBool(TOKENREQ_REDIRURI_ADD_PATHSUFFIX)) {
			return manipulateURI(getRedirectUri(), false, true);
		}

		return getRedirectUri();
	}

	protected ResponseType getAuthReqResponseType() {
		if (params.getBool(AUTHNREQ_RESPONSE_TYPE_TOKEN_IDTOKEN)) {
			return new ResponseType("token id_token");
		}

		// default
		return new ResponseType("code");
	}

	protected Scope getAuthReqScope() {
		Scope scopes = new Scope(
				OIDCScopeValue.OPENID,
				OIDCScopeValue.PROFILE,
				OIDCScopeValue.EMAIL);
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
		return null;
	}

	@Nullable
	protected ResponseMode getAuthReqResponseMode() {
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

	//
//		ClaimsRequest claims = new ClaimsRequest();
////		claims.addIDTokenClaim("group");
//		claims.addIDTokenClaim("profile");
//		claims.addIDTokenClaim("name");
//		claims.addIDTokenClaim("email");
//		claims.addUserInfoClaim("email");
//		claims.addIDTokenClaim("given_name");
//		claims.addIDTokenClaim("middle_name");
	@Nullable
	protected CodeChallenge getCodeChallenge() {
		return null;
	}

	@Nullable
	protected JWT getIdTokenHint() {
		return null;
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
		if (params.getBool(RPParameterConstants.REGISTER_CLIENTAUTH_POST)) {
			return new ClientAuthenticationMethod("client_secret_post");
		}
		if (params.getBool(RPParameterConstants.REGISTER_CLIENTAUTH_NONE)) {
			return new ClientAuthenticationMethod("client_secret_none");
		}

		// use Basic Auth per default (null):
		return null;
	}
}
