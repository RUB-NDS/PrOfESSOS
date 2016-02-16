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
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.claims.AccessTokenHash;
import com.nimbusds.openid.connect.sdk.claims.CodeHash;
import java.security.GeneralSecurityException;

/**
 *
 * @author Tobias Wich
 */
public class ReplayOP extends DefaultOP {

	@Override
	protected JWT getIdToken(ClientID clientId, Nonce nonce, AccessTokenHash atHash, CodeHash cHash)
			throws GeneralSecurityException, JOSEException, ParseException {
		Nonce newNonce;
		if (params.getBool(OPParameterConstants.FORCE_TOKEN_NONCE_EXCL)) {
			newNonce = null;
		} else if (params.getBool(OPParameterConstants.FORCE_TOKEN_NONCE_INVALID)) {
			newNonce = new Nonce();
		} else {
			newNonce = nonce;
		}

		return super.getIdToken(clientId, newNonce, atHash, cHash);
	}

}
