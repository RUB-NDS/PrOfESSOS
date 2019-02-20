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
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.test_model.TestStepResult;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class SessionOverwritingOP extends DefaultOP {

    @Override
    public void authRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            if (type == OPType.HONEST) {
                logger.log("Authentication requested at Honest OP.");
                HTTPRequest reqMsg = ServletUtils.createHTTPRequest(req);
                logger.logHttpRequest(req, reqMsg.getQuery());




//                CompletableFuture.runAsync(() -> {
//                    try {
//                        CompletableFuture<?> waitForEvil = (CompletableFuture<?>) stepCtx.get(OPContextConstants.BLOCK_HONEST_OP_FUTURE);
//                        waitForEvil.get(30, TimeUnit.SECONDS);
//                    } catch (InterruptedException|ExecutionException|TimeoutException e) {}
//                }).whenComplete((task, throwable) -> {
//                    if(throwable != null) {
//                       logger.log("timeout", throwable);
//                    } else {
//                        try {
//                            // forward request by the actual honest implementation
//                            logger.log("processing authreq in honest op");
//                            super.authRequest(path, req, resp);
//                        } catch (IOException e){logger.log("failed to respond", e);}
//                    }
//                });

				CompletableFuture<?> waitForEvil = (CompletableFuture<?>) stepCtx.get(OPContextConstants.BLOCK_HONEST_OP_FUTURE);
				waitForEvil.get(15, TimeUnit.SECONDS);
                // forward request by the actual honest implementation
                logger.log("processing authreq in honest op");
                super.authRequest(path, req, resp);

            } else {
                logger.log("Authentication requested at Evil OP.");
                HTTPRequest reqMsg = ServletUtils.createHTTPRequest(req);
                logger.logHttpRequest(req, reqMsg.getQuery());
                AuthenticationRequest authReq = AuthenticationRequest.parse(reqMsg);

                // extract values and save for later use
//                State opState = authReq.getState();
//                stepCtx.put(OPContextConstants.AUTH_REQ_EVIL_STATE, opState);
//                logger.log("State from Evil OP saved, releasing AuthResponse from Honest OP ");

//				resp.setStatus(204);
//				resp.flushBuffer();
                CompletableFuture<?> releaseHonest = (CompletableFuture<?>) stepCtx.get(OPContextConstants.BLOCK_HONEST_OP_FUTURE);
                logger.log("Releasing locked Honest-OP");
                releaseHonest.complete(null);
            }
        } catch (ParseException ex) {
            logger.log("Failed to parse Authorization Request.");
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
        catch (InterruptedException ex) {
            logger.log("Waiting for client to discover evil OP was interrupted.", ex);
        } catch (ExecutionException |TimeoutException ex) {
            logger.log("Waiting for client to discover evil failed.", ex);

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
                    // TODO compare actual code
                    // ^^ what ???
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

}
