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

import com.google.common.base.Strings;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.Subject;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Display;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.SubjectType;
import com.nimbusds.openid.connect.sdk.claims.AccessTokenHash;
import com.nimbusds.openid.connect.sdk.claims.CodeHash;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.rp.OIDCClientInformation;
import de.rub.nds.oidc.log.TestStepLogger;
import de.rub.nds.oidc.server.OPIVConfig;
import de.rub.nds.oidc.server.TestNotApplicableException;
import de.rub.nds.oidc.test_model.OPConfigType;
import de.rub.nds.oidc.test_model.ParameterType;
import de.rub.nds.oidc.utils.InstanceParameters;
import de.rub.nds.oidc.utils.SaveFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.mail.internet.InternetAddress;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateEncodingException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static de.rub.nds.oidc.server.TestStepParameterConstants.*;
import static de.rub.nds.oidc.server.op.OPParameterConstants.*;

/**
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
	protected InstanceParameters params;

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
	public OPType getOPType() {
		return type;
	}

	@Override
	public void setContext(Map<String, Object> suiteCtx, Map<String, Object> stepCtx) {
		this.suiteCtx = suiteCtx;
		this.stepCtx = stepCtx;
	}

	@Override
	public void setParameters(List<ParameterType> params) {
		this.params = new InstanceParameters(params);
	}


	protected void sendResponse(String typeName, Response errorResp, HttpServletResponse resp) throws IOException {
		HTTPResponse httpResp = errorResp.toHTTPResponse();
		ServletUtils.applyHTTPResponse(httpResp, resp);

		resp.flushBuffer();
		logger.log(type + "OP is returning " + typeName + " Response.");
		logger.logHttpResponse(resp, httpResp.getContent());
	}

	protected void sendErrorResponse(String typeName, ErrorResponse errorResp, HttpServletResponse resp) throws IOException {
		HTTPResponse httpResp = errorResp.toHTTPResponse();
		ServletUtils.applyHTTPResponse(httpResp, resp);

		resp.flushBuffer();
		logger.log(type + "OP is returning " + typeName + " Error Response.");
		logger.logHttpResponse(resp, httpResp.getContent());
	}


	protected Issuer getHonestIssuer() {
		return new Issuer(UriBuilder.fromUri(opivCfg.getHonestOPUri()).path(testId).build());
	}

	protected Issuer getEvilIssuer() {
		return new Issuer(UriBuilder.fromUri(opivCfg.getEvilOPUri())
				.path((String) stepCtx.getOrDefault(OPContextConstants.REGISTRATION_ENFORCING_PATH_FRAGMENT, ""))
				.path(testId).build());
	}

	protected Issuer getMetadataIssuer() {
		Issuer issuer;
		if (params.getBool(FORCE_HONEST_DISCOVERY_ISS)) {
			issuer = getHonestIssuer();
		} else {
			issuer = supplyHonestOrEvil(this::getHonestIssuer, this::getEvilIssuer);
		}
		return issuer;
	}


	protected String getTokenIssuerString() {
		if (params.getBool(FORCE_TOKEN_ISS_EXCL)) {
			return null;
		}
		if (params.getBool(FORCE_TOKEN_ISS_EMPTY)) {
			return "";
		}

		Issuer issuer;
		if (params.getBool(FORCE_HONEST_TOKEN_ISS)) {
			issuer = getHonestIssuer();
		} else {
			issuer = supplyHonestOrEvil(this::getHonestIssuer, this::getEvilIssuer);
		}
		return issuer.getValue();
	}


	protected Subject getHonestSubject() {
		return new Subject("honest-op-test-subject");
	}

	protected Subject getEvilSubject() {
		return new Subject("evil-op-test-subject");
	}

	protected Subject getTokenSubject() {
		Subject sub;
		if (params.getBool(FORCE_HONEST_TOKEN_SUB)) {
			sub = getHonestSubject();
		} else {
			sub = supplyHonestOrEvil(this::getHonestSubject, this::getEvilSubject);
		}
		return sub;
	}


	protected String getHonestName() {
		return "Honest User";
	}

	protected String getEvilName() {
		return "Evil User";
	}

	protected String getTokenName() {
		String name;
		if (params.getBool(FORCE_HONEST_TOKEN_NAME)) {
			name = getHonestName();
		} else {
			name = supplyHonestOrEvil(this::getHonestName, this::getEvilName);
		}
		return name;
	}


	protected String getHonestUsername() {
		return "honest-user-name";
	}

	protected String getEvilUsername() {
		return "evil-user-name";
	}

	protected String getTokenUsername() {
		String name;
		if (params.getBool(FORCE_HONEST_TOKEN_USERNAME)) {
			name = getHonestUsername();
		} else {
			name = supplyHonestOrEvil(this::getHonestUsername, this::getEvilUsername);
		}
		return name;
	}


	protected URI getHonestRegistrationEndpoint() {
		return UriBuilder.fromUri(opivCfg.getHonestOPUri()).path(testId).path(REGISTER_CLIENT_PATH).build();
	}

	protected URI getEvilRegistrationEndpoint() {
		return UriBuilder.fromUri(opivCfg.getEvilOPUri()).path(testId).path(REGISTER_CLIENT_PATH).build();
	}

	protected URI getMetadataRegistrationEndpoint() {
		URI uri;
		if (params.getBool(FORCE_HONEST_DISCOVERY_REG_EP)) {
			uri = getHonestRegistrationEndpoint();
		} else {
			uri = supplyHonestOrEvil(this::getHonestRegistrationEndpoint, this::getEvilRegistrationEndpoint);
		}
		return uri;
	}


	protected URI getHonestAuthorizationEndpoint() {
		return UriBuilder.fromUri(opivCfg.getHonestOPUri()).path(testId).path(AUTH_REQUEST_PATH).build();
	}

	protected URI getEvilAuthorizationEndpoint() {
		return UriBuilder.fromUri(opivCfg.getEvilOPUri()).path(testId).path(AUTH_REQUEST_PATH).build();
	}

	protected URI getMetadataAuthorizationEndpoint() {
		URI uri;
		if (params.getBool(FORCE_HONEST_DISCOVERY_AUTH_EP)) {
			uri = getHonestAuthorizationEndpoint();
		} else {
			uri = supplyHonestOrEvil(this::getHonestAuthorizationEndpoint, this::getEvilAuthorizationEndpoint);
		}
		return uri;
	}


	protected URI getHonestTokenEndpoint() {
		return UriBuilder.fromUri(opivCfg.getHonestOPUri()).path(testId).path(TOKEN_REQUEST_PATH).build();
	}

	protected URI getEvilTokenEndpoint() {
		return UriBuilder.fromUri(opivCfg.getEvilOPUri()).path(testId).path(TOKEN_REQUEST_PATH).build();
	}

	protected URI getMetadataTokenEndpoint() {
		URI uri;
		if (params.getBool(FORCE_HONEST_DISCOVERY_TOKEN_EP)) {
			uri = getHonestTokenEndpoint();
		} else {
			uri = supplyHonestOrEvil(this::getHonestTokenEndpoint, this::getEvilTokenEndpoint);
		}
		return uri;
	}


	protected URI getHonestUserinfoEndpoint() {
		return UriBuilder.fromUri(opivCfg.getHonestOPUri()).path(testId).path(USER_INFO_REQUEST_PATH).build();
	}

	protected URI getEvilUserinfoEndpoint() {
		return UriBuilder.fromUri(opivCfg.getEvilOPUri()).path(testId).path(USER_INFO_REQUEST_PATH).build();
	}

	protected URI getMetadataUserinfoEndpoint() {
		URI uri;
		if (params.getBool(FORCE_HONEST_DISCOVERY_AUTH_EP)) {
			uri = getHonestUserinfoEndpoint();
		} else {
			uri = supplyHonestOrEvil(this::getHonestUserinfoEndpoint, this::getEvilUserinfoEndpoint);
		}
		return uri;
	}


	protected InternetAddress getHonestEmail() {
		InternetAddress mail = new InternetAddress();
		mail.setAddress("user@honest.com");
		return mail;
	}

	protected InternetAddress getEvilEmail() {
		InternetAddress mail = new InternetAddress();
		mail.setAddress("user@evil.com");
		return mail;
	}

	protected InternetAddress getTokenEmail() {
		InternetAddress mail;
		if (params.getBool(FORCE_HONEST_TOKEN_EMAIL)) {
			mail = getHonestEmail();
		} else {
			mail = supplyHonestOrEvil(this::getHonestEmail, this::getEvilEmail);
		}
		return mail;
	}

	protected OIDCClientInformation getHonestRegisteredClientInfo() {
		// Note that ClientInfo @ Honest OP is stored in suite context and does not depend on test step setup
		OIDCClientInformation ci = (OIDCClientInformation) suiteCtx.get(OPContextConstants.REGISTERED_CLIENT_INFO_HONEST);
		return ci;
	}

	protected OIDCClientInformation getEvilRegisteredClientInfo() {
		OIDCClientInformation ci = (OIDCClientInformation) stepCtx.get(OPContextConstants.REGISTERED_CLIENT_INFO_EVIL);
		return ci;
	}

	protected OIDCClientInformation getRegisteredClientInfo() {
		OIDCClientInformation ci = supplyHonestOrEvil(this::getHonestRegisteredClientInfo, this::getEvilRegisteredClientInfo);
		return ci;
	}

	protected ClientID getRegistrationClientId() {
		OIDCClientInformation ci;
		if (params.getBool(OPParameterConstants.FORCE_REGISTER_HONEST_CLIENTID)) {
			ci = getHonestRegisteredClientInfo();
			if (ci != null && !Strings.isNullOrEmpty(ci.getID().toString())) {
				ClientID id = ci.getID();
				logger.log(String.format("Re-using client ID: %s", id.toString()));
				return id;
			} else {
				logger.log("ClientId at Honest OP could not be found.");
			}
		}
		logger.log("Generating random ClientID");
		return new ClientID();
	}

	protected Date getTokenIssuedAt() {
		Date date = new Date();
		if (params.getBool(FORCE_TOKEN_IAT_DAY)) {
			logger.log("Setting iat to 1 day.");
			date = Date.from(date.toInstant().plus(Duration.ofDays(1)));
		} else if (params.getBool(FORCE_TOKEN_IAT_YEAR)) {
			logger.log("Setting iat to 365 days.");
			date = Date.from(date.toInstant().plus(Period.ofDays(365)));
		}
		return date;
	}

	protected Date getTokenExpiration() {
		Date date = Date.from(Instant.now().plus(Duration.ofMinutes(15)));
		if (params.getBool(FORCE_TOKEN_EXP_DAY)) {
			logger.log("Setting exp to -1 day + 15min.");
			date = Date.from(date.toInstant().minus(Duration.ofDays(1)));
		} else if (params.getBool(FORCE_TOKEN_EXP_YEAR)) {
			logger.log("Setting exp to -365 days + 15min.");
			date = Date.from(date.toInstant().minus(Period.ofDays(365)));
		}
		return date;
	}


	protected OIDCProviderMetadata getDefaultOPMetadata() throws ParseException {
		Issuer issuer = getMetadataIssuer();
		List<SubjectType> subjectTypes = Arrays.asList(SubjectType.PUBLIC);
		URI jwksUri = UriBuilder.fromUri(baseUri).path(JWKS_PATH).build();
		OIDCProviderMetadata md = new OIDCProviderMetadata(issuer, subjectTypes, jwksUri);
		md.applyDefaults();

		// endpoints
		URI authzEndpt = getMetadataAuthorizationEndpoint();
		URI tokenEndpt = getMetadataTokenEndpoint();
		URI userInfoEndpt = getMetadataUserinfoEndpoint();
		URI registrationEndpt = getMetadataRegistrationEndpoint();
		md.setAuthorizationEndpointURI(authzEndpt);
		md.setTokenEndpointURI(tokenEndpt);
		md.setUserInfoEndpointURI(userInfoEndpt);
		md.setRegistrationEndpointURI(registrationEndpt);

		// , ResponseType.parse("id_token"), ResponseType.parse("token id_token"));
		Scope scopes = new Scope("openid", "name", "preferred_username", "email");
		List<ResponseType> responseTypes = Arrays.asList(ResponseType.parse("code"), ResponseType.parse("id_token"),
				ResponseType.parse("token id_token"), ResponseType.parse("code id_token token"));
		List<ResponseMode> responseModes = Arrays.asList(ResponseMode.QUERY, ResponseMode.FRAGMENT, ResponseMode.FORM_POST);
		List<GrantType> grantTypes = Arrays.asList(GrantType.AUTHORIZATION_CODE, GrantType.IMPLICIT);
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
		UserInfo ui = new UserInfo(getTokenSubject());
		ui.setName(getTokenName());
		ui.setPreferredUsername(getTokenUsername());
		ui.setEmail(getTokenEmail());

		return ui;
	}

	protected String getTokenAudience(ClientID clientId) {
		if (params.getBool(FORCE_TOKEN_AUD_EXCL)) {
			return null;
		} else if (params.getBool(FORCE_TOKEN_AUD_INVALID)) {
			return new ClientID().getValue();
		} else {
			return clientId.getValue();
		}
	}

	protected JWTClaimsSet getIdTokenClaims(@Nonnull ClientID clientId, @Nullable Nonce nonce,
											@Nullable AccessTokenHash atHash, @Nullable CodeHash cHash) throws ParseException {
		UserInfo ui = getUserInfo();
		if (params.getBool(FORCE_TOKEN_USERCLAIMS_EXCL)) {
			// reset all user claims except "sub"
			ui.setName(null);
			ui.setPreferredUsername(null);
			ui.setEmail(null);
		}

		JWTClaimsSet.Builder cb = new JWTClaimsSet.Builder(ui.toJWTClaimsSet());

		cb.issuer(getTokenIssuerString());
		cb.audience(getTokenAudience(clientId));
		cb.issueTime(getTokenIssuedAt());
		cb.expirationTime(getTokenExpiration());

		if (nonce != null) {
			cb.claim("nonce", nonce.getValue());
		}
		if (atHash != null) {
			String tokenHash = params.getBool(FORCE_TOKEN_AT_HASH_INVALID) ? "invalid_at_hash" : atHash.getValue();
			cb.claim("at_hash", tokenHash);
		}
		if (cHash != null) {
			String codeHash = params.getBool(FORCE_TOKEN_CODE_HASH_INVALID) ? "invalid_code_hash" : cHash.getValue();
			cb.claim("c_hash", codeHash);
		}

		JWTClaimsSet claims = cb.build();
		return claims;
	}

	protected JWT getIdToken(@Nonnull ClientID clientId, @Nullable Nonce nonce, @Nullable AccessTokenHash atHash,
							 @Nullable CodeHash cHash) throws GeneralSecurityException, JOSEException, ParseException {
		JWTClaimsSet claims = getIdTokenClaims(clientId, nonce, atHash, cHash);

		// HMAC with client secret
		if (params.getBool(OPParameterConstants.FORCE_IDTOKEN_SIGNING_ALG_HS256)) {
			OIDCClientInformation ci = getRegisteredClientInfo();
			Secret clientSecret = ci.getSecret();
			JWSSigner signer = new MACSigner(clientSecret.getValueBytes());

			JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT);
			JWSHeader header = headerBuilder.build();

			SignedJWT signedJwt = new SignedJWT(header, claims);
			signedJwt.sign(signer);
			return signedJwt;
		}

		// (Default) apply RSA Signature
		RSAKey key = getSigningJwk();

		JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.RS256)
				.type(JOSEObjectType.JWT);
		if (params.getBool(INCLUDE_SIGNING_CERT)) {
			headerBuilder = headerBuilder.jwk(key.toPublicJWK());
		}
		JWSHeader header = headerBuilder.build();

		SignedJWT signedJwt = new SignedJWT(header, claims);

		JWSSigner signer = new RSASSASigner(key);
		signedJwt.sign(signer);

		return signedJwt;
	}

	protected RSAKey getSigningJwk() {
		KeyStore.PrivateKeyEntry keyEntry = supplyHonestOrEvil(opivCfg::getHonestOPSigningEntry, opivCfg::getEvilOPSigningEntry);

		return getSigningJwk(keyEntry);
	}

	protected RSAKey getSigningJwk(KeyStore.PrivateKeyEntry keyEntry) {
		RSAPublicKey pubKey = (RSAPublicKey) keyEntry.getCertificate().getPublicKey();
		RSAPrivateKey privKey = (RSAPrivateKey) keyEntry.getPrivateKey();
		List<Base64> chain = Arrays.stream(keyEntry.getCertificateChain()).map(c -> {
			try {
				return Base64.encode(c.getEncoded());
			} catch (CertificateEncodingException ex) {
				throw new IllegalArgumentException("Failed to encode certificate.", ex);
			}
		}).collect(Collectors.toList());

		RSAKey.Builder kb = new RSAKey.Builder(pubKey)
				.privateKey(privKey)
				.x509CertChain(chain)
				.algorithm(JWSAlgorithm.RS256);

		String keyID = (String) stepCtx.getOrDefault(OPContextConstants.SIGNING_JWK_KEYID, "");
		if (!Strings.isNullOrEmpty(keyID)) {
			kb.keyID(keyID);
		}

		RSAKey key = kb.build();

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

	protected final <T> void saveHonestOrEvil(T value, SaveFunction<T> honestSaveFunc, SaveFunction<T> evilSaveFunc) {
		if (type == OPType.HONEST) {
			honestSaveFunc.save(value);
		} else if (type == OPType.EVIL) {
			evilSaveFunc.save(value);
		} else {
			throw new IllegalStateException("OP is neither honest nor evil.");
		}
	}

	protected void checkRpTestStepPreconditions(@Nullable AuthenticationRequest authnReq) throws TestNotApplicableException {
		checkDiscoveryPrecondition();
		if (authnReq != null) {
			checkResponseTypePrecondition(authnReq.getResponseType());
			checkScopePrecondition(authnReq.getScope());
		}
	}

	protected void checkResponseTypePrecondition(ResponseType respType)  throws TestNotApplicableException {
		boolean codeRequired = Boolean.parseBoolean((String) stepCtx.get(RESPONSE_TYPE_CONDITION_CODE));
		boolean tokenRequired = Boolean.parseBoolean((String) stepCtx.get(RESPONSE_TYPE_CONDITION_TOKEN));
		boolean idTokenRequired = Boolean.parseBoolean((String) stepCtx.get(RESPONSE_TYPE_CONDITION_IDTOKEN));

		StringBuilder sb = new StringBuilder();
		if (codeRequired && !respType.contains("code")) {
			sb.append("code ");
		}
		if (idTokenRequired && !respType.contains("id_token")) {
			sb.append("id_token ");
		}
		if (tokenRequired && !respType.contains("token")) {
			sb.append("token ");
		}

		String missingRTs = sb.toString().trim();
		if (!Strings.isNullOrEmpty(missingRTs)) {
			String msg = String.format("Test not applicable for requested response type. Expected response_type to " +
					"contain \"%s\"", missingRTs);
			logger.log("Test precondition not fulfilled, test aborted.");
			stepCtx.put(OPContextConstants.TEST_RUN_NOT_FINISHED, msg);
			throw new TestNotApplicableException("Requested response_type not Supported");
		}
	}

	protected void checkDiscoveryPrecondition() throws TestNotApplicableException {
		boolean discoRequired = Boolean.parseBoolean((String) stepCtx.get(DISCOVERY_REQUEST_REQUIRED));
		OPType discoReceivedAt = (OPType) stepCtx.get(OPContextConstants.DISCOVERY_REQUESTED_AT_OP_TYPE);
		if (discoRequired && discoReceivedAt == null) {
			logger.log("TestStep prerequisites not fulfilled: No discovery request was received but is required " +
					"before the first Authentication Request.");
			throw new TestNotApplicableException("Discovery Support required to run this test.");
		}
	}

	protected void checkScopePrecondition(Scope scope) throws TestNotApplicableException {
		boolean openIDRequired = !Boolean.parseBoolean((String) stepCtx.get(SCOPE_CONDITION_OPENID_NOT_NEEDED));
		if (openIDRequired && !scope.contains("openid")) {
			logger.log("TestStep prerequisites not fulfilled: Scope 'openid' not requested by client but ID Token validation" +
					"required for test execution and evaluation.");
			throw new TestNotApplicableException("OpenID Connect requires 'scope' to contain 'openid'.");
		}

	}
}
