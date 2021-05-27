/****************************************************************************
 * Copyright 2016 Ruhr-Universität Bochum.
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

package de.rub.nds.oidc.server.op;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.UserInfoRequest;
import com.nimbusds.openid.connect.sdk.UserInfoSuccessResponse;
import com.nimbusds.openid.connect.sdk.rp.OIDCClientInformation;
import de.rub.nds.oidc.test_model.TestStepResult;
import de.rub.nds.oidc.utils.Misc;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.CompletableFuture;

/**
 * @author Tobias Wich
 */
public class MaliciousEndpointOP extends DefaultOP {

	@Override
	protected OIDCTokenResponse tokenRequestInt(TokenRequest tokenReq, HttpServletResponse resp)
			throws GeneralSecurityException, JOSEException, ParseException {
		// extract values from request
		ClientAuthentication auth = tokenReq.getClientAuthentication();
		ClientID clientId = auth != null ? auth.getClientID() : tokenReq.getClientID();
		AuthorizationGrant grant = tokenReq.getAuthorizationGrant();
		AuthorizationCode code = null;
		if (grant != null && grant.getType() == GrantType.AUTHORIZATION_CODE) {
			AuthorizationCodeGrant codeGrant = (AuthorizationCodeGrant) grant;
			code = codeGrant.getAuthorizationCode();
		}

		// get values from honest OP for comparison
		OIDCClientInformation info = getHonestRegisteredClientInfo();
		ClientID refClientId = info.getID();
		AuthorizationCode refCode = (AuthorizationCode) stepCtx.get(OPContextConstants.HONEST_CODE);

		// compare values
		Object fo = stepCtx.get(OPContextConstants.TOKEN_INFORMATIONLEAK_FUTURE);
		CompletableFuture<TestStepResult> f = (CompletableFuture<TestStepResult>) fo;
		if (f != null) {
			TestStepResult result = null;
			if (refClientId != null && refClientId.equals(clientId)) {
				logger.log("Detected Honest ClientID in Evil OP.");
				result = TestStepResult.FAIL;
			} else if (clientId != null) {
				logger.log(String.format("Detected unknown ClientID %s in Evil OP.", clientId.toString()));
				result = TestStepResult.UNDETERMINED;
			}
			if (refCode != null && refCode.equals(code)) {
				logger.log("Detected Honest Code in Evil OP.");
				result = TestStepResult.FAIL;
			} else if (code != null) {
				logger.log("Detected unknown Code in Evil OP.");
				result = Misc.getWorst(TestStepResult.UNDETERMINED, result);
			}

			f.complete(result);
		}

		return super.tokenRequestInt(tokenReq, resp);
	}

	@Override
	protected UserInfoSuccessResponse userInfoRequestInt(UserInfoRequest userReq, HttpServletResponse resp)
			throws IOException {
		// extract values from request
		AccessToken at = userReq.getAccessToken();

		// get values from honest OP for comparison
		AccessToken refAt = (AccessToken) stepCtx.get(OPContextConstants.HONEST_ACCESSTOKEN);

		// compare values
		Object fo = stepCtx.get(OPContextConstants.USERINFO_INFORMATIONLEAK_FUTURE);
		CompletableFuture<TestStepResult> f = (CompletableFuture<TestStepResult>) fo;
		if (f != null) {
			if (refAt != null && refAt.equals(at)) {
				logger.log("Detected Honest AccessToken in Evil OP.");
				f.complete(TestStepResult.FAIL);
			} else if (at != null) {
				logger.log("Detected unknown AccessToken in Evil OP.");
				f.complete(TestStepResult.UNDETERMINED);
			}
		}

		return super.userInfoRequestInt(userReq, resp);
	}
}
