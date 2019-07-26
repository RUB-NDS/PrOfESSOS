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

import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.oauth2.sdk.pkce.CodeChallenge;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.openid.connect.sdk.AuthenticationResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.UserInfoResponse;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.test_model.TestStepResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

import static de.rub.nds.oidc.server.rp.RPContextConstants.BLOCK_BROWSER_AND_TEST_RESULT;
import static de.rub.nds.oidc.server.rp.RPParameterConstants.*;


public class PkceRP extends DefaultRP {
	private boolean firstrequest = true;
	private CodeVerifier firstVerifier;

	@Override
	public void callback(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException, URISyntaxException, ParseException {
		HTTPRequest httpRequest = ServletUtils.createHTTPRequest(req);
		logger.log("Callback received");
		logger.logHttpRequest(req, httpRequest.getQuery());

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
		if (successResponse.impliedResponseType().impliesCodeFlow()
				&& (!params.getBool(FORCE_NO_REDEEM_AUTH_CODE) || !firstrequest)) {
			// attempt code redemption
			TokenResponse tokenResponse = redeemAuthCode(successResponse.getAuthorizationCode());
			if (!tokenResponse.indicatesSuccess()) {
				// code redemption failed, error messages have been logged already
				browserBlocker.complete(TestStepResult.PASS);
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

		firstrequest = false;
		firstVerifier = getStoredPKCEVerifier();

		// generate authrequest for second
		prepareAuthnReq();

		if (idToken != null) {
			TestStepResult idTokenConditionResult = checkIdTokenCondition(idToken);
			if (idTokenConditionResult != null) {
				browserBlocker.complete(idTokenConditionResult);
				return;
			}
		}

		if (at != null) {
			UserInfoResponse userInfoResponse = requestUserInfo(at);
			if (userInfoResponse.indicatesSuccess()) {
				TestStepResult result = checkUserInfo(userInfoResponse, null);

				if (result != null) {
					browserBlocker.complete(TestStepResult.FAIL);
					return;
				}
			}
		}

		browserBlocker.complete(TestStepResult.PASS);
		return;
	}


	@Override
	protected void tokenRequestApplyPKCEParams(HTTPRequest req) {

		CodeVerifier verifier = getStoredPKCEVerifier();
		if (firstrequest) {
			firstVerifier = verifier;
		}
		if (!firstrequest && params.getBool(TOKENREQ_PKCE_FROM_OTHER_SESSION)) {
			verifier = firstVerifier;
		}
		if (params.getBool(TOKENREQ_PKCE_EXCLUDED)) {
			return;
		}

		String encodedQuery = req.getQuery();
		StringBuilder sb = new StringBuilder();
		sb.append(encodedQuery);

		if (params.getBool(TOKENREQ_ADD_PKCE_METHOD_PLAIN)) {
			// attempt "downgrade", use code_challenge from AuthnReq 
			// as code verifier in tokenreq and additionally
			// add 'plain' as code_challenge_method (not a valid param as per RFC7636)
			CodeChallengeMethod cm = getCodeChallengeMethod();
			cm = (cm == null) ? CodeChallengeMethod.S256 : cm;
			CodeChallenge cc = CodeChallenge.compute(cm, verifier);
			sb.append("&code_challenge_method=plain&code_verifier=");
			sb.append(cc.getValue());
			req.setQuery(sb.toString());
			return;
		}

		if (params.getBool(TOKENREQ_PKCE_INVALID)) {
			sb.append("&code_verifier=");
			// change last char
			// only certain ASCII chars are allowed; to keep it simple, use A or B
			String last = verifier.getValue().endsWith("A") ? "B" : "A";
			sb.append(verifier.getValue().substring(0, verifier.getValue().length() - 1) + last);
		} else {
			sb.append("&code_verifier=");
			sb.append(verifier.getValue());
		}

		req.setQuery(sb.toString());
	}
}
