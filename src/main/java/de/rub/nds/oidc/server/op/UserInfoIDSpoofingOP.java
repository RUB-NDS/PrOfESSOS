/****************************************************************************
 * Copyright 2019 Ruhr-Universität Bochum.
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

import com.nimbusds.common.contenttype.ContentType;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.token.BearerTokenError;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.UserInfoErrorResponse;
import com.nimbusds.openid.connect.sdk.UserInfoRequest;
import com.nimbusds.openid.connect.sdk.claims.AccessTokenHash;
import com.nimbusds.openid.connect.sdk.claims.CodeHash;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.utils.UnsafeJSONObject;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;

import static de.rub.nds.oidc.server.op.OPParameterConstants.INCLUDE_SIGNING_CERT;


public class UserInfoIDSpoofingOP extends DefaultOP {
	
		/**
		 * Returns custom UserInfo response as configured in TestPlan. In particular, this enables
		 * returning UserInfos that do not include a "sub" claim or include multiple "sub" claims
		 * in the order defined in the TestStepConfiguration. 
		 */
		@Override
		public void userInfoRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
			try {
				logger.log("User Info requested.");

				HTTPRequest httpReq = ServletUtils.createHTTPRequest(req);
				UserInfoRequest userReq = UserInfoRequest.parse(httpReq);
				logger.logHttpRequest(req, httpReq.getQuery());
			} catch (ParseException ex) {
				logger.log("Error parsing User Info Request.", ex);
				ErrorObject error = ex.getErrorObject();
				BearerTokenError be = new BearerTokenError(error.getCode(), error.getDescription(), error.getHTTPStatusCode());
				UserInfoErrorResponse errorResp = new UserInfoErrorResponse(be);
				sendErrorResponse("User Info", errorResp, resp);
			}

			UnsafeJSONObject userInfoJson = new UnsafeJSONObject();
			JSONArray subs = new JSONArray();

			for (Map.Entry param : params.getParamMap().entrySet()) {
				if (param.getKey().equals(OPParameterConstants.USERINFO_INCLUDE_HONEST_SUB) 
						&& param.getValue().equals("true")) {
					subs.appendElement(getHonestSubject());
				}
				if (param.getKey().equals(OPParameterConstants.USERINFO_INCLUDE_EVIL_SUB) 
						&& param.getValue().equals("true")) {
					subs.appendElement(getEvilSubject());
				}
			}
			if (params.getBool(OPParameterConstants.USERINFO_SUB_ARRAY)) {
				userInfoJson.append("sub", subs);
			} else {
				subs.forEach(s -> userInfoJson.append("sub", s.toString()));
			}
			
			if (params.getBool(OPParameterConstants.USERINFO_INCLUDE_HONEST_ISS)) {
				userInfoJson.append("iss", getHonestIssuer().getValue());
			}
			if (params.getBool(OPParameterConstants.USERINFO_INCLUDE_EVIL_ISS)) {
				userInfoJson.append("iss", getEvilIssuer().getValue());
			}

			if (params.getBool(OPParameterConstants.FORCE_HONEST_USERINFO_NAME)) {
				userInfoJson.append("name", getHonestName());
			} else {
				userInfoJson.append("name", supplyHonestOrEvil(super::getHonestName, super::getEvilName));
			}
			if (params.getBool(OPParameterConstants.FORCE_HONEST_USERINFO_EMAIL)) {
				userInfoJson.append("email", getHonestEmail().getAddress());
			} else {
				userInfoJson.append("email", supplyHonestOrEvil(super::getHonestEmail, super::getEvilEmail).getAddress());
			}
			if (params.getBool(OPParameterConstants.FORCE_HONEST_USERINFO_USERNAME)) {
				userInfoJson.append("preferred_username", getHonestUsername());
			} else {
				userInfoJson.append("preferred_username", supplyHonestOrEvil(super::getHonestUsername, super::getEvilUsername));
			}

			String content = userInfoJson.toJSONString();
			if (type == OPType.EVIL) {
				stepCtx.put(OPContextConstants.STORED_USERINFO_RESPONSE_EVIL, userInfoJson);
			}
			
			resp.setStatus(HttpServletResponse.SC_OK);
			resp.setContentType(ContentType.APPLICATION_JSON.toString()); 
			resp.setCharacterEncoding("UTF-8");
			resp.getWriter().write(content);
			resp.flushBuffer();

			logger.log("Returning UserInfo Response.");
			// set plain=true, otherwise the logger applies formatting and removes duplicate json keys
			logger.logHttpResponse(resp, content, false);
		}


	/**
	 * JWT spec allows to include certain claims in the JWT header, a feat mainly meant to 
	 * aid clients with processing of encrypted JWTs. OIDC does forbid porcessing of such
	 * claims in the header. However, naive implementations that borrow from JWE libraries
	 * may make use of these claims nonetheless.
	 */
	@Override
	public JWT getIdToken(@Nonnull ClientID clientId, @Nullable Nonce nonce, @Nullable AccessTokenHash atHash,
										  @Nullable CodeHash cHash) throws GeneralSecurityException, JOSEException, ParseException {
		if (!params.getBool(OPParameterConstants.FORCE_TOKENHEADER_CLAIMS)) {
			JWT token = super.getIdToken(clientId, nonce, atHash, cHash);
			storeTokenInContext(token);
			return token;
		}
		
		JWTClaimsSet claims = getIdTokenClaims(clientId, nonce, atHash, cHash);
		
		RSAKey key = getSigningJwk();

		JSONObject jsonHeader = new JSONObject();
		
		// "typ" header parameter is optional acc. to RFC7519, Section 5.1
		jsonHeader.put("typ", "JWT");
		jsonHeader.put("alg", "RS256");
		
		if (params.getBool(OPParameterConstants.FORCE_TOKENHEADER_HONEST_ISS)) {
			jsonHeader.put("iss", getHonestIssuer());
		}
		if (params.getBool(OPParameterConstants.FORCE_TOKENHEADER_HONEST_SUB)) {
			jsonHeader.put("sub", getHonestSubject());
		}
		if (params.getBool(OPParameterConstants.FORCE_TOKENHEADER_HONEST_EMAIL)) {
			jsonHeader.put("email", getHonestEmail());
		}
		if (params.getBool(INCLUDE_SIGNING_CERT)) {
			jsonHeader.put("jwk", key.toPublicJWK());
		}


		logger.logCodeBlock("Generated JWT Header:", jsonHeader.toJSONString());
		
		JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.RS256)
				.type(JOSEObjectType.JWT);
		headerBuilder.parsedBase64URL(Base64URL.encode(jsonHeader.toJSONString()));
		JWSHeader header = headerBuilder.build();

		SignedJWT signedJwt = new SignedJWT(header, claims);

		JWSSigner signer = new RSASSASigner(key);
		signedJwt.sign(signer);

		/*
			// TODO: TestCase with actual encryption is missing
			Retrieve JWK from client
			filter for RSA Public key qith use claim encryption
			encrypt 
			make sure the header still contatins the malicious sub claim fields
			send the encrypted jwk
			
		 */
		storeTokenInContext(signedJwt);
		return signedJwt;
	}

	private void storeTokenInContext(JWT token) {
		if (type == OPType.HONEST) {
			stepCtx.put(OPContextConstants.STORED_ID_TOKEN_HONEST, token);
		} else {
			stepCtx.put(OPContextConstants.STORED_ID_TOKEN_EVIL, token);
		}
	}

}
