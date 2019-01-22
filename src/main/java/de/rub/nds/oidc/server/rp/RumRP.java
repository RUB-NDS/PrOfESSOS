package de.rub.nds.oidc.server.rp;

import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.*;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.test_model.TestStepResult;
import org.apache.http.client.utils.URIBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RumRP extends AbstractRPImplementation {



	@Override
	public void callback(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException, URISyntaxException, ParseException {

		CompletableFuture<TestStepResult> browserBlocker = (CompletableFuture<TestStepResult>) stepCtx.get(RPContextConstants.BLOCK_BROWSER_AND_TEST_RESULT);


		AuthenticationSuccessResponse successResponse = processCallback(path, req, resp);
		if (successResponse == null) {
			logger.log("AuthenticationResponse Error");
			browserBlocker.complete(TestStepResult.PASS);
		}

		if (type.equals(RPType.EVIL) && params.getBool(RPParameterConstants.FORCE_AUTHNREQ_EVIL_REDIRURI)) {
			// received auth code in evil client
			logger.log("Authentication SuccessResponse received in Evil Client");
			logger.logHttpRequest(req, null);
			browserBlocker.complete(TestStepResult.FAIL);
			return;

		} else {
			logger.log("Authentication SuccessResponse received in Honest Client");
			logger.logHttpRequest(req, null);
//				browserBlocker.complete(TestStepResult.PASS);
//				return;
		}

		OIDCTokenResponse tokenResponse = fetchToken(successResponse);
		if (tokenResponse == null) {
			// error messages have been logged already
			browserBlocker.complete(TestStepResult.PASS);
			return;
		} else {
			logger.log("Code sucessfully redeemed");
			logger.logHttpResponse(tokenResponse.toHTTPResponse(), tokenResponse.toHTTPResponse().getContent());

			// for logging
			requestUserInfo(tokenResponse.getTokens().getAccessToken());

			browserBlocker.complete(TestStepResult.FAIL);
			return;
		}
	}


	@Override
	public void prepareAuthnReq() {

		URI redirURI = params.getBool(RPParameterConstants.FORCE_AUTHNREQ_EVIL_REDIRURI) ? getEvilRedirectUri() : getRedirectUri();

		AuthorizationRequest authnReq = new AuthorizationRequest(
				opMetaData.getAuthorizationEndpointURI(),
				new ResponseType("code"),
				null,
				clientInfo.getID(),
				redirURI,
				new Scope(OIDCScopeValue.OPENID, OIDCScopeValue.PROFILE, OIDCScopeValue.EMAIL),
				new State(32)
		);

		// store in stepCtx, so BrowserSimulator can fetch it
		String currentRP = type == RPType.HONEST ? RPContextConstants.RP1_PREPARED_AUTHNREQ
				: RPContextConstants.RP2_PREPARED_AUTHNREQ;
		stepCtx.put(currentRP, authnReq.toURI());
	}

}
