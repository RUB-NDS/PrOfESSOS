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

import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.GrantType;
import com.nimbusds.oauth2.sdk.OAuth2Error;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.test_model.TestStepResult;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.UriBuilder;

/**
 *
 * @author Tobias Wich
 */
public class IdPConfusionOP extends DefaultOP {

	private boolean firstRequest = true;

	@Override
	public void authRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		try {
			if (type == OPType.HONEST) {
				if (firstRequest) {
					logger.log("Authentication requested.");
					HTTPRequest reqMsg = ServletUtils.createHTTPRequest(req);
					logger.logHttpRequest(req, reqMsg.getQuery());

					firstRequest = false;
					AuthenticationRequest authReq = AuthenticationRequest.parse(reqMsg);
					// extract values and save for later use
					State opState = authReq.getState();
					Nonce opNonce = authReq.getNonce();
					stepCtx.put(OPContextConstants.AUTH_REQ_HONEST_STATE, opState);
					stepCtx.put(OPContextConstants.AUTH_REQ_HONEST_NONCE, opNonce);
					logger.log("State and Nonce from Honest OP saved.");

					CompletableFuture<?> blocker = (CompletableFuture<?>) stepCtx.get(OPContextConstants.BLOCK_BROWSER_WAITING_FOR_HONEST);
					blocker.complete(null);

					// don't care about return value
				} else {
					// handle the second request by the actual honest implementation
					super.authRequest(path, req, resp);
				}
			} else {
				logger.log("Authentication requested.");
				HTTPRequest reqMsg = ServletUtils.createHTTPRequest(req);
				logger.logHttpRequest(req, reqMsg.getQuery());

				State opState = (State) stepCtx.get(OPContextConstants.AUTH_REQ_HONEST_STATE);
				Nonce opNonce = (Nonce) stepCtx.get(OPContextConstants.AUTH_REQ_HONEST_NONCE);

				Map<String, String> authReqParams = reqMsg.getQueryParameters();
				if (opState != null) {
					// authReqParams.put("state", opState.toString());
				}
				if (opNonce != null) {
					authReqParams.put("nonce", opNonce.toString());
				}

				// build URI pointing to honest OP
				UriBuilder honestAuthReqUriBuilder = UriBuilder.fromUri(getHonestAuthorizationEndpoint());
				for (Map.Entry<String, String> next : authReqParams.entrySet()) {
					honestAuthReqUriBuilder = honestAuthReqUriBuilder.queryParam(next.getKey(), next.getValue());
				}
				URI honestAuthReqUri = honestAuthReqUriBuilder.build();
				resp.sendRedirect(honestAuthReqUri.toString());
				logger.log("Redirecting browser to honest OP.");
				logger.logHttpResponse(resp, null);
			}
		} catch (ParseException ex) {
			logger.log("Failed to parse Authorization Request.");
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
		}
	}

	@Override
	public void tokenRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		CompletableFuture<TestStepResult> blocker = (CompletableFuture<TestStepResult>) stepCtx.get(OPContextConstants.BLOCK_BROWSER_AND_TEST_RESULT);

		try {
			logger.log("Token requested.");

			HTTPRequest httpReq = ServletUtils.createHTTPRequest(req);
			TokenRequest tokenReq = TokenRequest.parse(httpReq);
			logger.logHttpRequest(req, httpReq.getQuery());

			if (type == OPType.EVIL) {
				AuthorizationGrant grant = tokenReq.getAuthorizationGrant();
				if (grant != null && grant.getType() == GrantType.AUTHORIZATION_CODE) {
					AuthorizationCodeGrant codeGrant = (AuthorizationCodeGrant) grant;
					AuthorizationCode code = codeGrant.getAuthorizationCode();
					// TODO compare actual code
					AuthorizationCode honestCode = (AuthorizationCode) stepCtx.get(OPContextConstants.HONEST_CODE);
					if (code.equals(honestCode)) {
						logger.log("Honest code received in attacker.");
						blocker.complete(TestStepResult.FAIL);
					} else {
						logger.log("Honest code not received in attacker.");
						blocker.complete(TestStepResult.PASS);
					}

					return;
				}
			}

			blocker.complete(TestStepResult.PASS);
		} catch (ParseException ex) {
			ErrorObject error = OAuth2Error.INVALID_REQUEST;
			TokenErrorResponse errorResp = new TokenErrorResponse(error);
			sendErrorResponse("Token", errorResp, resp);
			blocker.complete(TestStepResult.UNDETERMINED);
		}

	}

}
