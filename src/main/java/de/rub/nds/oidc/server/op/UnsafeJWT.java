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

import com.nimbusds.jose.Header;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import java.text.ParseException;
import java.util.Arrays;
import net.minidev.json.JSONObject;

/**
 *
 * @author Tobias Wich
 */
public class UnsafeJWT implements JWT {

	private Base64URL header;
	private Base64URL payload;
	private Base64URL signature;

	public UnsafeJWT(Base64URL header, Base64URL payload) {
		this(header, payload, null);
	}

	public UnsafeJWT(Base64URL header, Base64URL payload, Base64URL signature) {
		this.header = header;
		this.payload = payload;
		this.signature = signature;
	}


	@Override
	public Header getHeader() {
		try {
			return Header.parse(header);
		} catch (ParseException ex) {
			throw new RuntimeException("Error parsing header.");
		}
	}

	@Override
	public JWTClaimsSet getJWTClaimsSet() throws ParseException {
		// code copied from SignedJWT
		JSONObject json = JSONObjectUtils.parseJSONObject(payload.decodeToString());
		if (json == null) {
			throw new ParseException("Payload of JWS object is not a valid JSON object", 0);
		}
		return JWTClaimsSet.parse(json);
	}

	@Override
	public Base64URL[] getParsedParts() {
		Base64URL[] result = Arrays.asList(header, payload, signature)
				.stream().filter(p -> p != null)
				.toArray(Base64URL[]::new);
		return result;
	}

	@Override
	public String getParsedString() {
		String result = Arrays.asList(getParsedParts()).stream()
				.map(Base64URL::toString)
				.reduce((a, b) -> a.concat(".").concat(b))
				.get();
		return result;
	}

	@Override
	public String serialize() {
		return getParsedString();
	}

}
