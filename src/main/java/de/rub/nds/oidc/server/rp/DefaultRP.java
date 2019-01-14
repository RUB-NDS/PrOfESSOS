package de.rub.nds.oidc.server.rp;

import com.google.common.base.Strings;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.openid.connect.sdk.*;
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DefaultRP extends AbstractRPImplementation {



    @Override
    public void callback(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException, URISyntaxException, ParseException {

		//TODO: this will not parse form_post responses
		HTTPRequest httpRequest = ServletUtils.createHTTPRequest(req);
		logger.log("Callback received");
		logger.logHttpRequest(req, httpRequest.getQuery());

		// send some response to the waiting browser and wait for browser confirmation
		sendCallbackResponse(resp);
		CompletableFuture waitForBrowser = (CompletableFuture) stepCtx.get(RPContextConstants.BLOCK_RP_FOR_BROWSER_FUTURE);
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

		// parse the URL from Browser to include potential URI Fragments (implicit/hybrid flows)
		AuthenticationResponse authnResp = AuthenticationResponseParser.parse(callbackUri);

		if (authnResp instanceof AuthenticationErrorResponse) {
			String opAuthEndp = opMetaData.getAuthorizationEndpointURI().toString();
			String user = (String) stepCtx.get(RPContextConstants.CURRENT_USER_USERNAME);
			String pass = (String) stepCtx.get(RPContextConstants.CURRENT_USER_USERNAME);
			logger.log(String.format("Authentication at %s as %s with password %s failed:", opAuthEndp, user, pass));
			logger.logHttpRequest(req, req.getQueryString());
			// store auth failed hint in context for browser retrieval
			stepCtx.put(RPContextConstants.RP_INDICATED_STEP_RESULT, TestStepResult.FAIL);
			return;
		}

		AuthenticationSuccessResponse successResponse = authnResp.toSuccessResponse();
		if (successResponse.getAccessToken() != null || successResponse.getIDToken() != null ) {
			// implicit or hybrid flow
			requestUserInfo(successResponse.getAccessToken());
			// TODO: any checks on received id_token?
		} else {
			// in code flow, fetch token/idToken
			OIDCTokenResponse tokenResponse = fetchToken(successResponse);
			if (tokenResponse == null) {
				// error messages have been logged already
				return;
			}
			OIDCTokens tokens = tokenResponse.toSuccessResponse().getOIDCTokens();
			requestUserInfo(tokens.getAccessToken());
		}

		// TODO: evaluation of test result, scope, whatever
		// check if the received id_token matches stepCtx.get(RPContextConstants.CURRENT_USER_USERNAME, username);
		// or maybe introduce some kind of user needle?
		// put RP_RESULT_INDICATION into stepCtx
		//


		stepCtx.put(RPContextConstants.RP_INDICATED_STEP_RESULT, TestStepResult.PASS);

		CompletableFuture<TestStepResult> browserBlocker = (CompletableFuture<TestStepResult>) stepCtx.get(RPContextConstants.BLOCK_BROWSER_AND_TEST_RESULT);
		browserBlocker.complete(TestStepResult.PASS);
		logger.log("released browser lock");
		return;
    }

    @Nullable
    private UserInfo requestUserInfo(AccessToken at) throws IOException, ParseException {
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

    @Nullable
    private OIDCTokenResponse fetchToken(AuthenticationSuccessResponse successResponse) throws IOException, ParseException{
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

		TokenResponse response = OIDCTokenResponseParser.parse(request.toHTTPRequest().send());

		if (!response.indicatesSuccess()) {
			TokenErrorResponse errorResponse = response.toErrorResponse();
			logger.log(String.format("Code redemption failed, received: %s", errorResponse.toString()));
			stepCtx.put(RPContextConstants.RP_INDICATED_STEP_RESULT, TestStepResult.FAIL);
			return null;
		}

		OIDCTokenResponse tokenSuccessResponse = (OIDCTokenResponse)response.toSuccessResponse();
		logger.log("Code redeemed for Token:");
		logger.logHttpResponse(response.toHTTPResponse(), response.toHTTPResponse().getContent());

//		JWT idToken = tokenSuccessResponse.getOIDCTokens().getIDToken();
//		AccessToken accessToken = tokenSuccessResponse.getOIDCTokens().getAccessToken();
//		RefreshToken refreshToken = tokenSuccessResponse.getOIDCTokens().getRefreshToken();
		return tokenSuccessResponse;
	}
    private void sendCallbackResponse(HttpServletResponse resp) throws IOException, ParseException {

		// send some response to dangling browser
		HTTPResponse httpRes = null;
		StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html>");
		sb.append("<html>");
		sb.append("<head><title>PrOfESSOS Callback</title></head>");
		sb.append("<body>");
		sb.append("<h2>PrOfESSOS OP Verifier</h2><strong>Callback confirmation</strong>");
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
}
