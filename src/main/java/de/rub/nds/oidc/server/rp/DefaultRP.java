package de.rub.nds.oidc.server.rp;

import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.openid.connect.sdk.*;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.test_model.TestStepResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DefaultRP extends AbstractRPImplementation {



    @Override
    public void callback(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException, URISyntaxException, ParseException {

		// TODO: this will not parse form_post responses
		HTTPRequest httpRequest = ServletUtils.createHTTPRequest(req);
		logger.log("Callback received");
		logger.logHttpRequest(req, httpRequest.getQuery());

		CompletableFuture waitForBrowser = (CompletableFuture) stepCtx.get(RPContextConstants.BLOCK_RP_FOR_BROWSER_FUTURE);
		CompletableFuture<TestStepResult> browserBlocker = (CompletableFuture<TestStepResult>) stepCtx.get(RPContextConstants.BLOCK_BROWSER_AND_TEST_RESULT);

		// send some response to the waiting browser and wait for browser confirmation
		sendCallbackResponse(resp);

		try {
			waitForBrowser.get(5, TimeUnit.SECONDS);
		} catch (TimeoutException| ExecutionException |InterruptedException e) {
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
			return;
		}

		AuthenticationSuccessResponse successResponse = authnResp.toSuccessResponse();

		UserInfo userInfo = null;
		if (successResponse.getAccessToken() != null ) {
			// implicit or hybrid flow
			userInfo = requestUserInfo(successResponse.getAccessToken());
			// TODO: any checks on received id_token?
		} else {
			// in code flow, fetch token/idToken
			OIDCTokenResponse tokenResponse = fetchToken(successResponse);
			if (tokenResponse == null) {
				// error messages have been logged already
				browserBlocker.complete(TestStepResult.UNDETERMINED);
				return;
			}
			OIDCTokens tokens = tokenResponse.toSuccessResponse().getOIDCTokens();
			userInfo = requestUserInfo(tokens.getAccessToken());
		}

		// TODO: evaluation of test result, userinfo, scope, whatever
		// check if the received id_token matches stepCtx.get(RPContextConstants.CURRENT_USER_USERNAME, username);
		// or maybe introduce some kind of user needle?
		// put RP_RESULT_INDICATION into stepCtx
		//
		// temporary: iterate first level keys of userinfo and compare w username
		if (userInfo != null) {
			userInfo.toJSONObject().forEach((k,v) -> {
				if (v.equals(user)) {
					logger.log(String.format("UserName %s matches %s entry in received UserInfo", user, k.toString() ));
					return;
				}
			});
		}

		stepCtx.put(RPContextConstants.RP_INDICATED_STEP_RESULT, TestStepResult.PASS);

//		logger.log("release browser lock");
		browserBlocker.complete(TestStepResult.PASS);
		return;
    }


}
