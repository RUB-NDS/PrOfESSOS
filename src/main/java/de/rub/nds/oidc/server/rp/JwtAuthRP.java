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

package de.rub.nds.oidc.server.rp;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretJWT;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import de.rub.nds.oidc.server.op.UnsafeJWT;
import net.minidev.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;

import static de.rub.nds.oidc.server.rp.RPParameterConstants.*;


public class JwtAuthRP extends DefaultRP {

	@Override
	protected URI getRegistrationJwkSetURI() {
		if (params.getBool(REGISTER_CLIENTAUTH_PK_JWT)) {
			return type == RPType.HONEST ? getHonestJwkSetUri() : getEvilJwkSetUri();
		}
		return null;
	}

	protected void tokenRequestApplyClientAuth(HTTPRequest req) {
		try {
			// remove Authorization header, if any
			req.setHeader("Authorization", null);

			// Prepare a dummy "client_assertion" query parameter
			Secret assertionSecret;
			if (clientInfo.getSecret() == null ||
				clientInfo.getSecret().getValueBytes().length < 32) {
				assertionSecret = new Secret();
				// we should leave a message about this:
				logger.log("Generated a new client Secret as dummy key for the client_assert HMAC generation");
			} else {
				assertionSecret = clientInfo.getSecret();
			}

			ClientAuthentication clientAuth = new ClientSecretJWT(clientInfo.getID(), opMetaData.getTokenEndpointURI(),
					JWSAlgorithm.HS256, assertionSecret);
			String clientAssert = ((ClientSecretJWT) clientAuth).getClientAssertion().serialize();

			SignedJWT orig = (SignedJWT) JWTParser.parse(clientAssert);
//			logger.logCodeBlock("original client_assertion JWT:", orig.serialize());

			String none = null;
			if (params.getBool(TOKENREQ_CLIENTSECRET_JWT_NONE_ALG)) {
				none = "none";
			} else if (params.getBool(TOKENREQ_CLIENTSECRET_JWT_NONE_ALG_MIXEDCASE)) {
				none = "nONe";
			}

			JSONObject newHdr = orig.getHeader().toJSONObject();
			if (none != null) {
				newHdr.replace("alg", none);
			}
			Base64URL newHdr64 = Base64URL.encode(newHdr.toString());

			Base64URL newSig64;
			if (params.getBool(TOKENREQ_CLIENTSECRET_JWT_INVALID_SIG)) {
				// copied from SignatureManipulationOp
				byte[] newSig = orig.getSignature().decode();
				newSig[0] = (byte) (newSig[0] ^ 0xFF); // flip bits in first byte
				newSig64 = Base64URL.encode(newSig);
			} else {
				newSig64 = orig.getSignature();
			}

			// generate the manipulated clientAssertion
			JWT newJwt;
			if (params.getBool(TOKENREQ_CLIENTSECRET_JWT_EXCLUDE_SIG)) {
				newJwt = new UnsafeJWT(newHdr64, orig.getPayload().toBase64URL());
			} else {
				newJwt = new UnsafeJWT(newHdr64, orig.getPayload().toBase64URL(), newSig64);
			}
			// add the clientAssertion to the TokenRequest query
			StringBuilder sb = new StringBuilder();
			sb.append(req.getQuery());
			sb.append("&client_assertion_type=urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer");
			sb.append("&client_assertion=");
			sb.append(URLEncoder.encode(newJwt.serialize(), "utf-8"));
			// some faulty OPs require additional client_id param
			sb.append("&client_id=");
			sb.append(getClientID().getValue());
			String newQuery = sb.toString();

			req.setQuery(newQuery);
//			logger.logCodeBlock("manipulated client_assertion parameter:", newJwt.serialize());

		} catch (JOSEException | java.text.ParseException | UnsupportedEncodingException e) {
			logger.log("Error generating client_secret_jwt", e);
		}
	}

}
