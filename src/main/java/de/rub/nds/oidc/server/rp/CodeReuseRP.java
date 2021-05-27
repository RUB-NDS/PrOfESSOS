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
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.openid.connect.sdk.AuthenticationResponse;
import com.nimbusds.openid.connect.sdk.UserInfoResponse;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.test_model.TestStepResult;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


public class CodeReuseRP extends DefaultRP {

//	private boolean isFirstCallback = true;

	@Override
	public void callback(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException, URISyntaxException, ParseException {

		CompletableFuture<TestStepResult> browserBlocker = (CompletableFuture<TestStepResult>) stepCtx.get(RPContextConstants.BLOCK_BROWSER_AND_TEST_RESULT);
		AuthorizationCode oldAuthcode = (AuthorizationCode) stepCtx.get(RPContextConstants.STORED_AUTH_CODE);

		AuthenticationResponse response = processCallback(req, resp, path);
		if (!response.indicatesSuccess()) {
			logger.log("AuthenticationResponse Error");
			browserBlocker.complete(TestStepResult.UNDETERMINED);
			return;
		}

		UserInfo userInfo = null;
		TokenResponse tokenResponse;

		if (oldAuthcode == null) {
			// store auth code for later retrieval
			AuthorizationCode code = response.toSuccessResponse().getAuthorizationCode();
			stepCtx.put(RPContextConstants.STORED_AUTH_CODE, code);

			if (!params.getBool(RPParameterConstants.FORCE_NO_REDEEM_AUTH_CODE)) {
				// redeem token
				tokenResponse = redeemAuthCode(code);
				if (!tokenResponse.indicatesSuccess()) {
					// error messages have been logged already
					browserBlocker.complete(TestStepResult.UNDETERMINED);
					return;
				}
//				OIDCTokens tokens = tokenResponse.toSuccessResponse().getOIDCTokens();
			}
			browserBlocker.complete(TestStepResult.PASS);
			return;
		} else {
			// replace code with oldAuthCode
//			URIBuilder ub = new URIBuilder(response.getRedirectionURI().toString());
//			ub.setParameter("code", oldAuthcode.getValue());
			// attempt to redeem hijacked authorization code
			tokenResponse = redeemAuthCode(oldAuthcode);

			if (!tokenResponse.indicatesSuccess()) {
				logger.log("Redemption of invalid authorization code was rejected by OP");
				browserBlocker.complete(TestStepResult.PASS);
				return;
			}
			// code has been accepted by OP
			if (params.getBool(RPParameterConstants.TOKEN_RECEIVAL_FAILS_TEST)) {
				browserBlocker.complete(TestStepResult.FAIL);
				return;
			}

			AccessToken token = tokenResponse.toSuccessResponse().getTokens().getAccessToken();
			String found = checkUserInfo(token, new String[]{testOPConfig.getUser1Name(), testOPConfig.getUser2Name()});
			if (!Strings.isNullOrEmpty(found)) {
				TestStepResult res = found.equals(testOPConfig.getUser1Name()) ? TestStepResult.FAIL : TestStepResult.PASS;
				browserBlocker.complete(res);
			} else {
				// TODO: is this always a pass?
				browserBlocker.complete(TestStepResult.PASS);
			}
		}
	}


	@Nullable
	private String checkUserInfo(AccessToken token, String[] users) throws ParseException, IOException {
		//UserInfo userInfo = requestUserInfo(token).toSuccessResponse().getUserInfo();
		String result = null;

		UserInfoResponse userInfo = requestUserInfo(token);
		if (userInfo != null && userInfo.indicatesSuccess()) {
			UserInfo ui = userInfo.toSuccessResponse().getUserInfo();

			// search all root level objects if their value matches either of the usernames
			for (String usern : users) {
				for (Map.Entry e : ui.toJSONObject().entrySet()) {
					if (e.getValue().equals(usern)) {
						logger.log(String.format("UserName %s matches %s entry in received UserInfo", usern, e.getKey().toString()));
						result = usern;
					}
				}
			}
		}
		return result;
	}
}
