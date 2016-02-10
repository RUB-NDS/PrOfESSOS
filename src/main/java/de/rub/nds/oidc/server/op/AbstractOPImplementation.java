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
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.ErrorResponse;
import com.nimbusds.oauth2.sdk.GrantType;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.Response;
import com.nimbusds.oauth2.sdk.ResponseMode;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.Subject;
import com.nimbusds.openid.connect.sdk.Display;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.SubjectType;
import com.nimbusds.openid.connect.sdk.claims.AccessTokenHash;
import com.nimbusds.openid.connect.sdk.claims.CodeHash;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import de.rub.nds.oidc.log.TestStepLogger;
import de.rub.nds.oidc.server.OPIVConfig;
import de.rub.nds.oidc.test_model.OPConfigType;
import de.rub.nds.oidc.test_model.ParameterType;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateEncodingException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.UriBuilder;

/**
 *
 * @author Tobias Wich
 */
public abstract class AbstractOPImplementation implements OPImplementation {

	protected OPConfigType cfg;
	protected OPIVConfig opivCfg;
	protected TestStepLogger logger;
	protected String testId;
	protected URI baseUri;
	protected OPType type;
	protected Map<String, Object> suiteCtx;
	protected Map<String, Object> stepCtx;
	protected Map<String, String> params;

	@Override
	public void setOPConfig(OPConfigType cfg) {
		this.cfg = cfg;
	}

	@Override
	public void setOPIVConfig(OPIVConfig opivCfg) {
		this.opivCfg = opivCfg;
	}

	@Override
	public void setLogger(TestStepLogger logger) {
		this.logger = logger;
	}

	@Override
	public void setTestId(String testId) {
		this.testId = testId;
	}

	@Override
	public void setBaseUri(URI baseUri) {
		this.baseUri = baseUri;
	}

	@Override
	public void setOPType(OPType type) {
		this.type = type;
	}

	@Override
	public void setContext(Map<String, Object> suiteCtx, Map<String, Object> stepCtx) {
		this.suiteCtx = suiteCtx;
		this.stepCtx = stepCtx;
	}

	@Override
	public void setParameters(List<ParameterType> params) {
		this.params = params.stream()
				.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
	}


	protected void sendResponse(String typeName, Response errorResp, HttpServletResponse resp) throws IOException {
		HTTPResponse httpResp = errorResp.toHTTPResponse();
		ServletUtils.applyHTTPResponse(httpResp, resp);

		resp.flushBuffer();
		logger.log("Returning " + typeName + " Response.");
		logger.logHttpResponse(resp, httpResp.getContent());
	}

	protected void sendErrorResponse(String typeName, ErrorResponse errorResp, HttpServletResponse resp) throws IOException {
		HTTPResponse httpResp = errorResp.toHTTPResponse();
		ServletUtils.applyHTTPResponse(httpResp, resp);

		resp.flushBuffer();
		logger.log("Returning " + typeName + " Error Response.");
		logger.logHttpResponse(resp, httpResp.getContent());
	}

	protected abstract Issuer getIssuer();

	protected OIDCProviderMetadata getDefaultOPMetadata() throws ParseException {
		Issuer issuer = getIssuer();
		List<SubjectType> subjectTypes = Arrays.asList(SubjectType.PUBLIC);
		URI jwksUri = UriBuilder.fromUri(baseUri).path(JWKS_PATH).build();
		OIDCProviderMetadata md = new OIDCProviderMetadata(issuer, subjectTypes, jwksUri);
		md.applyDefaults();

		// endpoints
		URI authzEndpt = UriBuilder.fromUri(baseUri).path(AUTH_REQUEST_PATH).build();
		URI tokenEndpt = UriBuilder.fromUri(baseUri).path(TOKEN_REQUEST_PATH).build();
		URI userInfoEndpt = UriBuilder.fromUri(baseUri).path(USER_INFO_REQUEST_PATH).build();
		URI registrationEndpt = UriBuilder.fromUri(baseUri).path(REGISTER_CLIENT_PATH).build();
		md.setAuthorizationEndpointURI(authzEndpt);
		md.setTokenEndpointURI(tokenEndpt);
		md.setUserInfoEndpointURI(userInfoEndpt);
		md.setRegistrationEndpointURI(registrationEndpt);

		// , ResponseType.parse("id_token"), ResponseType.parse("token id_token"));
		Scope scopes = new Scope("openid");
		List<ResponseType> responseTypes = Arrays.asList(ResponseType.parse("code"));
		List<ResponseMode> responseModes = Arrays.asList(ResponseMode.QUERY);
		List<GrantType> grantTypes = Arrays.asList(GrantType.AUTHORIZATION_CODE);
		md.setScopes(scopes);
		md.setResponseTypes(responseTypes);
		md.setResponseModes(responseModes);
		md.setGrantTypes(grantTypes);

		// algorithms
		List<JWSAlgorithm> jwsAlgs = Arrays.asList(JWSAlgorithm.RS256, JWSAlgorithm.parse("none"));
		md.setIDTokenJWSAlgs(jwsAlgs);

		List<ClientAuthenticationMethod> authMethods = Arrays.asList(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
		md.setTokenEndpointAuthMethods(authMethods);

		List<Display> displays = Arrays.asList(Display.PAGE);
		md.setDisplays(displays);

		return md;
	}

	protected UserInfo getUserInfo() {
		UserInfo ui = new UserInfo(new Subject("opiv-test-subject"));

		return ui;
	}

	protected JWT getIdToken(@Nonnull ClientID clientId, @Nullable Nonce nonce, @Nullable AccessTokenHash atHash,
			@Nullable CodeHash cHash) throws GeneralSecurityException, JOSEException, ParseException {
		UserInfo ui = getUserInfo();

		JWTClaimsSet.Builder cb = new JWTClaimsSet.Builder(ui.toJWTClaimsSet());

		cb.issuer(baseUri.toString());
		cb.audience(clientId.getValue());
		cb.issueTime(new Date());
		cb.expirationTime(Date.from(Instant.now().plus(Duration.ofMinutes(15))));

		if (nonce != null) {
			cb.claim("nonce", nonce.getValue());
		}
		if (atHash != null) {
			cb.claim("at_hash", atHash.getValue());
		}
		if (cHash != null) {
			cb.claim("c_hash", cHash.getValue());
		}

		JWTClaimsSet claims = cb.build();

		RSAKey key = getSigningJwk();

		JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
				.type(JOSEObjectType.JWT)
				.jwk(key.toPublicJWK())
				.build();

		SignedJWT signedJwt = new SignedJWT(header, claims);

		JWSSigner signer = new RSASSASigner(key);
		signedJwt.sign(signer);

		return signedJwt;
	}

	protected RSAKey getSigningJwk() {
		KeyStore.PrivateKeyEntry keyEntry = supplyHonestOrEvil(opivCfg::getHonestOPSigningEntry, opivCfg::getEvilOPSigningEntry);

		RSAPublicKey pubKey = (RSAPublicKey) keyEntry.getCertificate().getPublicKey();
		RSAPrivateKey privKey = (RSAPrivateKey) keyEntry.getPrivateKey();
		List<Base64> chain = Arrays.stream(keyEntry.getCertificateChain()).map(c -> {
			try {
				return Base64.encode(c.getEncoded());
			} catch (CertificateEncodingException ex) {
				throw new IllegalArgumentException("Failed to encode certificate.", ex);
			}
		}).collect(Collectors.toList());

		RSAKey key = new RSAKey.Builder(pubKey)
				.privateKey(privKey)
				.x509CertChain(chain)
				.algorithm(JWSAlgorithm.RS256)
				.build();

		return key;
	}

	protected final <T> T supplyHonestOrEvil(Supplier<T> honestSupplier, Supplier<T> evilSupplier) {
		if (type == OPType.HONEST) {
			return honestSupplier.get();
		} else if (type == OPType.EVIL) {
			return evilSupplier.get();
		} else {
			throw new IllegalStateException("OP is neither honest nor evil.");
		}
	}

}
