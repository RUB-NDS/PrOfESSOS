package de.rub.nds.oidc.server.rp;

import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.openid.connect.sdk.AuthenticationResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationResponseParser;
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.test_model.TestStepResult;
import org.apache.http.client.utils.URIBuilder;

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
		AuthorizationCode oldAuthcode = (AuthorizationCode) stepCtx.get(RPContextConstants.STORED_AUTH_CODE); // TODO beware of typecast exception

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
//			isFirstCallback = false;
			browserBlocker.complete(TestStepResult.PASS);
			return;
		} else {
			// replace code with oldAuthCode
			URIBuilder ub = new URIBuilder(response.getRedirectionURI().toString());
			ub.setParameter("code", oldAuthcode.getValue());
			AuthenticationResponse authnResp = AuthenticationResponseParser.parse(ub.build()).toSuccessResponse();
			// attempt to redeem hijacked authorization code
//			logger.logHttpResponse();
			tokenResponse = redeemAuthCode(oldAuthcode);
			if (tokenResponse.indicatesSuccess()) {
				AccessToken token = tokenResponse.toSuccessResponse().getTokens().getAccessToken();
				String found = checkUserInfo(token, new String[] {testOPConfig.getUser1Name(), testOPConfig.getUser2Name()});

				TestStepResult res = found.equals(testOPConfig.getUser1Name()) ? TestStepResult.FAIL : TestStepResult.PASS;
				browserBlocker.complete(res);
				return;

			} else {
				logger.log("Redemption of invalid authorization code was rejected by OP");
				browserBlocker.complete(TestStepResult.PASS);
				return;
			}
		}
    }


    @Nullable
    private String checkUserInfo(AccessToken token, String [] users) throws ParseException, IOException {
		UserInfo userInfo = requestUserInfo(token).toSuccessResponse().getUserInfo();

		// search all root level objects if their value matches either of the usernames
		String result = null;
		if (userInfo != null) {
			for (String usern : users) {
				for (Map.Entry e :userInfo.toJSONObject().entrySet() ) {
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
