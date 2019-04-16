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
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.client.ClientRegistrationErrorResponse;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.token.BearerTokenError;
import com.nimbusds.openid.connect.sdk.*;
import com.nimbusds.openid.connect.sdk.claims.AccessTokenHash;
import com.nimbusds.openid.connect.sdk.claims.CodeHash;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.rp.OIDCClientInformation;
import com.nimbusds.openid.connect.sdk.rp.OIDCClientInformationResponse;
import com.nimbusds.openid.connect.sdk.rp.OIDCClientMetadata;
import com.nimbusds.openid.connect.sdk.rp.OIDCClientRegistrationRequest;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.server.TestNotApplicableException;
import net.minidev.json.JSONObject;
import org.apache.commons.text.StringEscapeUtils;

import javax.annotation.Nullable;
import javax.json.Json;
import javax.json.JsonObject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

/**
 * @author Tobias Wich
 */
public class DefaultOP extends AbstractOPImplementation {

	@Override
	public void webfinger(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		logger.log("Received webfinger request.");
		logger.logHttpRequest(req, null);
		String rel = req.getParameter("rel");
		String resource = req.getParameter("resource");
		// TODO this should be a final constant
		String href = resource.contains("enforce-rp-reg") ? resource : path.getDispatchUriAndTestId().toString();
		if ("http://openid.net/specs/connect/1.0/issuer".equals(rel)) {
			JsonObject result = Json.createObjectBuilder()
					.add("subject", resource)
					.add("links", Json.createArrayBuilder().add(Json.createObjectBuilder()
							.add("rel", "http://openid.net/specs/connect/1.0/issuer")
							.add("href", href)))
					.build();
			StringWriter sw = new StringWriter();
			Json.createWriter(sw).writeObject(result);
			resp.getWriter().write(sw.toString());
			resp.setContentType("application/json; charset=UTF-8");

			resp.flushBuffer();
			logger.logHttpResponse(resp, sw.toString());
		} else {
			// return not handled
			String msg = "Missing webfinger parameters in request.";
			logger.log(msg);
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, msg);
		}
	}

	@Override
	public void providerConfiguration(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		logger.log("Provider configuration requested.");
		logger.logHttpRequest(req, null);
		try {

			OIDCProviderMetadata md = getDefaultOPMetadata();
			String mdStr = md.toJSONObject().toString();

			resp.setContentType("application/json");
			resp.getWriter().write(mdStr);

			resp.flushBuffer();
			logger.log("Returning default provider config.");
			logger.logHttpResponse(resp, mdStr);

		} catch (IOException | ParseException ex) {
			logger.log("Failed to process default provider config.", ex);
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.flushBuffer();
			logger.logHttpResponse(resp, null);
		}
	}

	@Override
	public void jwks(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		logger.log("JWK Set requested.");
		logger.logHttpRequest(req, null);

		ArrayList<JWK> jwks = new ArrayList<>();
		jwks.add(getSigningJwk());

		JWKSet result = new JWKSet(jwks);
		JSONObject json = result.toJSONObject(true);
		String jsonStr = json.toString();

		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		resp.getWriter().write(jsonStr);
		resp.flushBuffer();

		logger.log("Returning JWK set.");
		logger.logHttpResponse(resp, jsonStr);
	}

	@Override
	public void registerClient(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		logger.log("Client registration requested.");
		// parse request
		HTTPRequest httpReq = ServletUtils.createHTTPRequest(req);
		logger.logHttpRequest(req, httpReq.getQuery());

		OIDCClientInformation info = supplyHonestOrEvil(
				() -> (OIDCClientInformation) suiteCtx.get(OPContextConstants.REGISTERED_CLIENT_INFO_HONEST),
				() -> (OIDCClientInformation) stepCtx.get(OPContextConstants.REGISTERED_CLIENT_INFO_EVIL));

		// check if the client is already registered
		try {
			if (info == null) {
				OIDCClientRegistrationRequest regReq = OIDCClientRegistrationRequest.parse(httpReq);
				OIDCClientMetadata clientMd = regReq.getOIDCClientMetadata();

				// create info object and safe it in context aka register the client
				ClientID id = getRegistrationClientId();
				Secret secret = new Secret();
				info = new OIDCClientInformation(id, new Date(), clientMd, secret);

				// replace some of the values with our own
				info.getOIDCMetadata().setScope(Scope.parse("openid"));

				if (type == OPType.HONEST) {
					suiteCtx.put(OPContextConstants.REGISTERED_CLIENT_INFO_HONEST, info);
				} else {
					stepCtx.put(OPContextConstants.REGISTERED_CLIENT_INFO_EVIL, info);
				}

				// answer the response
				OIDCClientInformationResponse regResp = new OIDCClientInformationResponse(info);
				HTTPResponse httpRes = regResp.toHTTPResponse();
				ServletUtils.applyHTTPResponse(httpRes, resp);

				resp.flushBuffer();
				logger.log("Returning Client Information Response.");
				logger.logHttpResponse(resp, regResp.toHTTPResponse().getContent());
			} else {
				// client is already registered
				// answer the response
				OIDCClientInformationResponse regResp = new OIDCClientInformationResponse(info);
				HTTPResponse httpRes = regResp.toHTTPResponse();
				ServletUtils.applyHTTPResponse(httpRes, resp);

				resp.flushBuffer();
				logger.log("Returning Client Information Response.");
				logger.logHttpResponse(resp, regResp.toHTTPResponse().getContent());
			}
		} catch (ParseException ex) {
			ErrorObject error = new ErrorObject("invalid_client_metadata", ex.getMessage());
			ClientRegistrationErrorResponse errorRes = new ClientRegistrationErrorResponse(error);
			ServletUtils.applyHTTPResponse(errorRes.toHTTPResponse(), resp);

			resp.flushBuffer();
			logger.log("Returning Client Information Error Response.");
			logger.logHttpResponse(resp, errorRes.toHTTPResponse().getContent());
		}
	}

	protected State getState(AuthenticationRequest authReq) {
		return authReq.getState();
	}

	@Override
	public void authRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		logger.log("Authentication requested.");
		HTTPRequest reqMsg = ServletUtils.createHTTPRequest(req);
		logger.logHttpRequest(req, reqMsg.getQuery());

		try {
			AuthenticationRequest authReq = AuthenticationRequest.parse(reqMsg);

			URI redirectUri = authReq.getRedirectionURI();
			State state = getState(authReq);
			Nonce nonce = authReq.getNonce();
			ResponseType responseType = authReq.getResponseType();
			ResponseMode responseMode = authReq.getResponseMode();

			try {
				checkTestStepConditions(authReq);

				AuthorizationCode code = null;
				CodeHash cHash = null;
				if (responseType.contains("code")) {
					code = new AuthorizationCode();
					cHash = CodeHash.compute(code, JWSAlgorithm.RS256);
					// save code if honest op
					if (type == OPType.HONEST) {
						stepCtx.put(OPContextConstants.HONEST_CODE, code);
					}
				}

				AccessToken at = null;
				AccessTokenHash atHash = null;
				if (responseType.contains("token")) {
					at = new BearerAccessToken();
					atHash = AccessTokenHash.compute(at, JWSAlgorithm.RS256);
					// save token if honest op
					if (type == OPType.HONEST) {
						stepCtx.put(OPContextConstants.HONEST_ACCESSTOKEN, at);
					}
				}

				JWT idToken = null;
				if (responseType.contains("id_token")) {
					idToken = getIdToken(authReq.getClientID(), authReq.getNonce(), atHash, cHash);
				}

				HTTPResponse httpRes = null;
				if (responseMode != null && responseMode.equals(ResponseMode.FORM_POST)) {
					AuthenticationSuccessResponse authRes = new AuthenticationSuccessResponse(redirectUri, code, idToken,
							at, state, null, ResponseMode.FORM_POST);

					StringBuilder sb = new StringBuilder();
					sb.append("<!DOCTYPE html>");
					sb.append("<html>");
					sb.append("<head><title>PrOfESSOS form post</title></head>");
					sb.append("<body onload=\"javascript:document.forms[0].submit()\">");
					sb.append("<form method=\"post\" action=\"" + authRes.getRedirectionURI().toString() + "\">");
					for (Map.Entry<String, String> entry : authRes.toParameters().entrySet()) {
						String entryValue = StringEscapeUtils.escapeHtml4(entry.getValue());
						sb.append("<input type=\"hidden\" name=\"" + entry.getKey() + "\" value=\"" + entryValue + "\"/>");
					}
					sb.append("</form>");
					sb.append("</body>");
					sb.append("</html>");

					httpRes = new HTTPResponse(HTTPResponse.SC_OK);
					httpRes.setContentType("text/html; charset=UTF-8");
					httpRes.setHeader("Cache-Control", "no-cache, no-store");
					httpRes.setHeader("Pragma", "no-cache");
					httpRes.setContent(sb.toString());
				} else {
					AuthenticationResponse authRes = new AuthenticationSuccessResponse(redirectUri, code, idToken,
							at, state, null, null);
					httpRes = authRes.toHTTPResponse();
				}
				ServletUtils.applyHTTPResponse(httpRes, resp);

				// save nonce for the token request
				stepCtx.put(OPContextConstants.AUTH_REQ_NONCE, nonce);

				resp.flushBuffer();
				logger.log("Returning default Authorization Response.");
				logger.logHttpResponse(resp, httpRes.getContent());
			} catch (GeneralSecurityException | JOSEException | TestNotApplicableException ex) {
				ErrorObject errorCode = OAuth2Error.SERVER_ERROR;
				AuthenticationErrorResponse error = new AuthenticationErrorResponse(redirectUri, errorCode, state, ResponseMode.QUERY);
				HTTPResponse httpResp = error.toHTTPResponse();
				ServletUtils.applyHTTPResponse(httpResp, resp);

				resp.flushBuffer();
				logger.log(String.format("Authentication Request processing resulted in: %s(%s).",
						ex.getClass().getName(), ex.getMessage()));
				logger.log("Returning Authorization Error Response.");
				logger.logHttpResponse(resp, httpResp.getContent());
			}
		} catch (ParseException ex) {
			logger.log("Failed to parse Authorization Request.");
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
		}
	}

	@Override
	public void tokenRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		try {
			logger.log("Token requested.");

			HTTPRequest httpReq = ServletUtils.createHTTPRequest(req);
			TokenRequest tokenReq = TokenRequest.parse(httpReq);
			logger.logHttpRequest(req, httpReq.getQuery());

			OIDCTokenResponse tokenRes = tokenRequestInt(tokenReq, resp);
			if (tokenRes != null) {
				sendResponse("Token", tokenRes, resp);
			}
		} catch (GeneralSecurityException | JOSEException ex) {
			ErrorObject error = OAuth2Error.SERVER_ERROR;
			TokenErrorResponse errorResp = new TokenErrorResponse(error);
			sendErrorResponse("Token", errorResp, resp);
		} catch (ParseException ex) {
			ErrorObject error = OAuth2Error.INVALID_REQUEST;
			TokenErrorResponse errorResp = new TokenErrorResponse(error);
			sendErrorResponse("Token", errorResp, resp);
		}
	}

	@Nullable
	protected OIDCTokenResponse tokenRequestInt(TokenRequest tokenReq, HttpServletResponse resp)
			throws GeneralSecurityException, JOSEException, ParseException {
		ClientAuthentication auth = tokenReq.getClientAuthentication();
		ClientID clientId = auth != null ? auth.getClientID() : tokenReq.getClientID();
		AuthorizationGrant grant = tokenReq.getAuthorizationGrant();
		CodeHash cHash = null;
		if (grant != null && grant.getType() == GrantType.AUTHORIZATION_CODE) {
			AuthorizationCodeGrant codeGrant = (AuthorizationCodeGrant) grant;
			cHash = CodeHash.compute(codeGrant.getAuthorizationCode(), JWSAlgorithm.RS256);
		}

		AccessToken at = new BearerAccessToken();
		AccessTokenHash atHash = AccessTokenHash.compute(at, JWSAlgorithm.RS256);
		// save access token if honest op
		if (type == OPType.HONEST) {
			stepCtx.put(OPContextConstants.HONEST_ACCESSTOKEN, at);
		}

		Nonce nonce = (Nonce) stepCtx.get(OPContextConstants.AUTH_REQ_NONCE);

		JWT idToken = getIdToken(clientId, nonce, atHash, cHash);

		OIDCTokens tokens = new OIDCTokens(idToken, at, null);
		OIDCTokenResponse tokenRes = new OIDCTokenResponse(tokens);

		// save the token request target (Honest OP or Evil OP)
		stepCtx.put(OPContextConstants.TOKEN_REQ_RECEIVED_AT_OP_TYPE, type);

		return tokenRes;
	}


	@Override
	public void userInfoRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		try {
			logger.log("User Info requested.");

			HTTPRequest httpReq = ServletUtils.createHTTPRequest(req);
			UserInfoRequest userReq = UserInfoRequest.parse(httpReq);
			logger.logHttpRequest(req, httpReq.getQuery());

			UserInfoSuccessResponse uiResp = userInfoRequestInt(userReq, resp);
			if (uiResp != null) {
				sendResponse("User Info", uiResp, resp);
			}
		} catch (ParseException ex) {
			logger.log("Error parsing User Info Request.", ex);
			ErrorObject error = ex.getErrorObject();
			BearerTokenError be = new BearerTokenError(error.getCode(), error.getDescription(), error.getHTTPStatusCode());
			UserInfoErrorResponse errorResp = new UserInfoErrorResponse(be);
			sendErrorResponse("User Info", errorResp, resp);
		}
	}


	@Nullable
	protected UserInfoSuccessResponse userInfoRequestInt(UserInfoRequest userReq, HttpServletResponse resp)
			throws IOException {
		AccessToken at = userReq.getAccessToken();
		if (at == null) {
			UserInfoErrorResponse errorResp = new UserInfoErrorResponse(BearerTokenError.MISSING_TOKEN);
			sendErrorResponse("User Info", errorResp, resp);
			return null;
		}
		//AccessTokenHash atHash = AccessTokenHash.compute(at, JWSAlgorithm.RS256);

		UserInfo ui = getUserInfo();
		UserInfoSuccessResponse uiResp = new UserInfoSuccessResponse(ui);

		// save request target
		stepCtx.put(OPContextConstants.USERINFO_REQ_RECEIVED_AT_OP_TYPE, type);

		return uiResp;
	}

	@Override
	public void untrustedKeyRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		logger.log("Unexpected untrustedKeyRequest received");
		HTTPRequest httpReq = ServletUtils.createHTTPRequest(req);
		logger.logHttpRequest(req, httpReq.getQuery());

		resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
	}

}
