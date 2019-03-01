package de.rub.nds.oidc.server.rp;

import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.openid.connect.sdk.*;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.test_model.TestStepResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

import static de.rub.nds.oidc.server.rp.RPParameterConstants.SUCCESSFUL_CODE_REDEMPTION_FAILS_TEST;

public class RumRP extends DefaultRP {


	@Override
	public void callback(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException, URISyntaxException, ParseException {

		CompletableFuture<TestStepResult> browserBlocker = (CompletableFuture<TestStepResult>) stepCtx.get(RPContextConstants.BLOCK_BROWSER_AND_TEST_RESULT);

		AuthenticationResponse response = processCallback(req, resp, path);

		String manipulator = (String) stepCtx.get(RPContextConstants.REDIRECT_URI_MANIPULATOR);

		// callback received with error response
		if (!response.indicatesSuccess()) {
			if (type.equals(RPType.EVIL)) {
				logger.log("Authentication ErrorResponse received in Evil Client");
				logger.log("This may indicate an open redirector that could be chained to leak AuthCodes - please check manually.");
				browserBlocker.complete(TestStepResult.UNDETERMINED);
				return;
			}
			if (manipulator != null && req.getRequestURL().toString().contains(manipulator)) {
				logger.log("Authentication ErrorResponse sent to manipulated redirect_uri.");
				// TODO: if oidc => fail, if oauth => undetermined
				browserBlocker.complete(TestStepResult.FAIL);
				return;
			}
			
			logger.log("AuthenticationResponse Error received in Honest Client");
			logger.logHttpRequest(req, response.toString());
			browserBlocker.complete(TestStepResult.PASS);
			return;
		}

		// callback received with successful authentication response incl. code/token
		if (type.equals(RPType.EVIL)) {
			// received auth code in evil client
			logger.log("Authentication SuccessResponse received in Evil Client");
			logger.logHttpRequest(req, null);
			browserBlocker.complete(TestStepResult.FAIL);
			return;
		} 
	
		logger.log("Authentication SuccessResponse received in Honest Client");
		logger.logHttpRequest(req, null);


		if (manipulator != null && req.getRequestURL().toString().contains(manipulator)) {
			logger.log("Authentication Response sent to manipulated redirect_uri.");
			logger.log("Authorization Server does not perform exact string matching on redirect_uri.");
			// TODO: only fail for OIDC as OAUTH does not require exact matching afaik
			browserBlocker.complete(TestStepResult.FAIL);
			return;
		}


		// in codeHijacking tests, try to redeem the code
		AuthenticationSuccessResponse successResponse = response.toSuccessResponse();
		if (successResponse.impliedResponseType().impliesCodeFlow()) {
			// attempt code redemption
			TokenResponse tokenResponse = redeemAuthCode(successResponse.getAuthorizationCode());
			if (!tokenResponse.indicatesSuccess()) {
				// code redemption failed, error messages have been logged already
				browserBlocker.complete(TestStepResult.PASS);
				return;
			}

			OIDCTokens tokens = ((OIDCTokenResponse) tokenResponse).getOIDCTokens();
			logger.log("TokenRequest successful: " + tokens.toJSONObject().toJSONString());

			if (params.getBool(SUCCESSFUL_CODE_REDEMPTION_FAILS_TEST)) {
				logger.log("AuthorizationCode successfully redeemed, assuming test failed.");
				browserBlocker.complete(TestStepResult.FAIL);
				return;
			}
			
			browserBlocker.complete(TestStepResult.PASS);
		}
		
		
//		else if (successResponse.impliedResponseType().impliesImplicitFlow()) {
//			at = successResponse.getAccessToken();
//			idToken = successResponse.getIDToken();
//		}



			// do we need to issue a token request?
//		OIDCTokenResponse tokenResponse = redeemAuthCode(successResponse);
//		if (tokenResponse == null) {
//			// error messages have been logged already
//			browserBlocker.complete(TestStepResult.PASS);
//			return;
//		} else {
//			logger.log("Code sucessfully redeemed");
//			logger.logHttpResponse(tokenResponse.toHTTPResponse(), tokenResponse.toHTTPResponse().getContent());
//
//			// for logging
//			requestUserInfo(tokenResponse.getTokens().getAccessToken());
//
//			browserBlocker.complete(TestStepResult.FAIL);
//			return;
//		}
		}



	
//	@Override
//	@Nullable
//	protected URI getAuthReqRedirectUri() {
//		URI redirURI = params.getBool(AUTHNREQ_FORCE_EVIL_REDIRURI) ? getEvilRedirectUri() : getRedirectUri();
//		
//		boolean subdom = params.getBool(RPParameterConstants.AUTHNREQ_ADD_SUBDOMAIN_REDIRURI);
//		boolean path = params.getBool(RPParameterConstants.AUTHNREQ_ADD_PATHSUFFIX_REDIRURI);
//		if (subdom || path) {
//			return manipulateURI(redirURI, subdom, path);
//		}
//	
//		return redirURI;
//	}
	

}
