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

import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.test_model.TestStepResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class SessionOverwritingOP extends DefaultOP {

	@Override
	public void authRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {

		if (isFirstAuthReq()) {
			logger.log(String.format("Authentication requested at %s-OP.", type.name()));

			CompletableFuture<?> waitFor2ndAuthReq = new CompletableFuture<>();
			stepCtx.put(OPContextConstants.BLOCK_OP_FUTURE, waitFor2ndAuthReq);
			CompletableFuture<?> browserLock = (CompletableFuture<?>) stepCtx.get(OPContextConstants.BLOCK_BROWSER_WAITING_FOR_HONEST);

			HTTPRequest reqMsg = ServletUtils.createHTTPRequest(req);
			logger.logHttpRequest(req, reqMsg.getQuery());

			CompletableFuture<?> backgroundWaiter = CompletableFuture.runAsync(() -> {
				try {
					waitFor2ndAuthReq.get(15, TimeUnit.SECONDS);
					// forward request to the actual honest implementation
					logger.log(String.format("Start processing Authentication Request in %s-OP", type.name()));
					super.authRequest(path, req, resp);
				} catch (InterruptedException | TimeoutException | ExecutionException | IOException e) {
					logger.log("Exception while waiting to release first Authentication Response", e);
				}
			});

			// signal browser to start second auth request within the same session
			browserLock.complete(null);

			// wait for the AuthnResp before flushing response
			backgroundWaiter.join();
		} else {
			logger.log(String.format("Authentication requested at %s-OP.", type.name()));
			HTTPRequest reqMsg = ServletUtils.createHTTPRequest(req);
			logger.logHttpRequest(req, reqMsg.getQuery());

			// send nop response to waiting browser
			resp.setStatus(204);
			resp.flushBuffer();
			CompletableFuture<?> waitFor2ndAuthReq = (CompletableFuture<?>) stepCtx.get(OPContextConstants.BLOCK_OP_FUTURE);
			logger.log("Releasing delayed AuthResponse");
			waitFor2ndAuthReq.complete(null);
		}
	}

	// TODO: this does not work for implicit flow - need to also check for userinforeqeusts (?)
	@Override
	public void tokenRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		CompletableFuture<TestStepResult> blocker = (CompletableFuture<TestStepResult>) stepCtx.get(OPContextConstants.BLOCK_BROWSER_AND_TEST_RESULT);

		// send error response in either case
		ErrorObject error = OAuth2Error.INVALID_REQUEST;
		TokenErrorResponse errorResp = new TokenErrorResponse(error);

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

					AuthorizationCode honestCode = (AuthorizationCode) stepCtx.get(OPContextConstants.HONEST_CODE);
					if (code.equals(honestCode)) {
						logger.log("Honest code received in attacker.");
						blocker.complete(TestStepResult.FAIL);
					} else {
						logger.log("Honest code not received in attacker.");
						blocker.complete(TestStepResult.PASS);
					}
					sendErrorResponse("Token", errorResp, resp);
					return;
				}
			}
			sendErrorResponse("Token", errorResp, resp);
			blocker.complete(TestStepResult.PASS);
		} catch (ParseException ex) {
			sendErrorResponse("Token", errorResp, resp);
			blocker.complete(TestStepResult.UNDETERMINED);
		}

	}

	private boolean isFirstAuthReq() {
		return !stepCtx.containsKey(OPContextConstants.BLOCK_OP_FUTURE);
	}

}
