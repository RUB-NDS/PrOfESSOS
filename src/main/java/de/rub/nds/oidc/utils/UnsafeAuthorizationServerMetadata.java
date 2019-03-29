/*
 * oauth2-oidc-sdk
 *
 * Copyright 2012-2016, Connect2id Ltd and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package de.rub.nds.oidc.utils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.langtag.LangTag;
import com.nimbusds.langtag.LangTagException;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.util.JSONObjectUtils;
import com.nimbusds.oauth2.sdk.util.OrderedJSONObject;
import net.minidev.json.JSONObject;


/**
 * This is a verbatim copy of com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata, except
 * that we remove the restrictions regarding the 'none' algorithm: We do not want to forbid
 * potentially insecure OP configurations, as this is part of what we are looking for...
 */
public class UnsafeAuthorizationServerMetadata {

	/**
	 * The registered parameter names.
	 */
	private static final Set<String> REGISTERED_PARAMETER_NAMES;


	static {
		Set<String> p = new HashSet<>();
		p.add("issuer");
		p.add("authorization_endpoint");
		p.add("token_endpoint");
		p.add("registration_endpoint");
		p.add("jwks_uri");
		p.add("scopes_supported");
		p.add("response_types_supported");
		p.add("response_modes_supported");
		p.add("grant_types_supported");
		p.add("code_challenge_methods_supported");
		p.add("token_endpoint_auth_methods_supported");
		p.add("token_endpoint_auth_signing_alg_values_supported");
		p.add("request_object_signing_alg_values_supported");
		p.add("request_object_encryption_alg_values_supported");
		p.add("request_object_encryption_enc_values_supported");
		p.add("ui_locales_supported");
		p.add("service_documentation");
		p.add("op_policy_uri");
		p.add("op_tos_uri");
		p.add("introspection_endpoint");
		p.add("introspection_endpoint_auth_methods_supported");
		p.add("introspection_endpoint_auth_signing_alg_values_supported");
		p.add("revocation_endpoint");
		p.add("revocation_endpoint_auth_methods_supported");
		p.add("revocation_endpoint_auth_signing_alg_values_supported");
		p.add("tls_client_certificate_bound_access_tokens");
		REGISTERED_PARAMETER_NAMES = Collections.unmodifiableSet(p);
	}


	/**
	 * Gets the registered OpenID Connect provider metadata parameter
	 * names.
	 *
	 * @return The registered OpenID Connect provider metadata parameter
	 *         names, as an unmodifiable set.
	 */
	public static Set<String> getRegisteredParameterNames() {

		return REGISTERED_PARAMETER_NAMES;
	}


	/**
	 * The issuer.
	 */
	private final Issuer issuer;


	/**
	 * The authorisation endpoint.
	 */
	private URI authzEndpoint;


	/**
	 * The token endpoint.
	 */
	private URI tokenEndpoint;


	/**
	 * The registration endpoint.
	 */
	private URI regEndpoint;


	/**
	 * The token introspection endpoint.
	 */
	private URI introspectionEndpoint;


	/**
	 * The token revocation endpoint.
	 */
	private URI revocationEndpoint;


	/**
	 * The JWK set URI.
	 */
	private URI jwkSetURI;


	/**
	 * The supported scope values.
	 */
	private Scope scope;


	/**
	 * The supported response types.
	 */
	private List<ResponseType> rts;


	/**
	 * The supported response modes.
	 */
	private List<ResponseMode> rms;


	/**
	 * The supported grant types.
	 */
	private List<GrantType> gts;


	/**
	 * The supported code challenge methods for PKCE.
	 */
	private List<CodeChallengeMethod> codeChallengeMethods;


	/**
	 * The supported token endpoint authentication methods.
	 */
	private List<ClientAuthenticationMethod> tokenEndpointAuthMethods;


	/**
	 * The supported JWS algorithms for the {@code private_key_jwt} and
	 * {@code client_secret_jwt} token endpoint authentication methods.
	 */
	private List<JWSAlgorithm> tokenEndpointJWSAlgs;


	/**
	 * The supported introspection endpoint authentication methods.
	 */
	private List<ClientAuthenticationMethod> introspectionEndpointAuthMethods;


	/**
	 * The supported JWS algorithms for the {@code private_key_jwt} and
	 * {@code client_secret_jwt} introspection endpoint authentication
	 * methods.
	 */
	private List<JWSAlgorithm> introspectionEndpointJWSAlgs;


	/**
	 * The supported revocation endpoint authentication methods.
	 */
	private List<ClientAuthenticationMethod> revocationEndpointAuthMethods;


	/**
	 * The supported JWS algorithms for the {@code private_key_jwt} and
	 * {@code client_secret_jwt} revocation endpoint authentication
	 * methods.
	 */
	private List<JWSAlgorithm> revocationEndpointJWSAlgs;


	/**
	 * The supported JWS algorithms for request objects.
	 */
	private List<JWSAlgorithm> requestObjectJWSAlgs;


	/**
	 * The supported JWE algorithms for request objects.
	 */
	private List<JWEAlgorithm> requestObjectJWEAlgs;


	/**
	 * The supported encryption methods for request objects.
	 */
	private List<EncryptionMethod> requestObjectJWEEncs;


	/**
	 * If {@code true} the {@code request} parameter is supported, else
	 * not.
	 */
	private boolean requestParamSupported = false;


	/**
	 * If {@code true} the {@code request_uri} parameter is supported, else
	 * not.
	 */
	private boolean requestURIParamSupported = true;


	/**
	 * If {@code true} the {@code request_uri} parameters must be
	 * pre-registered with the provider, else not.
	 */
	private boolean requireRequestURIReg = false;


	/**
	 * The supported UI locales.
	 */
	private List<LangTag> uiLocales;


	/**
	 * The service documentation URI.
	 */
	private URI serviceDocsURI;


	/**
	 * The provider's policy regarding relying party use of data.
	 */
	private URI policyURI;


	/**
	 * The provider's terms of service.
	 */
	private URI tosURI;


	/**
	 * If {@code true} the
	 * {@code tls_client_certificate_bound_access_tokens} if set, else
	 * not.
	 */
	private boolean tlsClientCertificateBoundAccessTokens = false;


	/**
	 * Custom (not-registered) parameters.
	 */
	private final JSONObject customParameters = new JSONObject();


	/**
	 * Creates a new OAuth 2.0 Authorisation Server (AS) metadata instance.
	 *
	 * @param issuer The issuer identifier. Must be an URI using the https
	 *               scheme with no query or fragment component. Must not
	 *               be {@code null}.
	 */
	public UnsafeAuthorizationServerMetadata(final Issuer issuer) {

		URI uri;
		try {
			uri = new URI(issuer.getValue());
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("The issuer identifier must be a URI: " + e.getMessage(), e);
		}

		if (uri.getRawQuery() != null)
			throw new IllegalArgumentException("The issuer URI must be without a query component");

		if (uri.getRawFragment() != null)
			throw new IllegalArgumentException("The issuer URI must be without a fragment component");

		this.issuer = issuer;
	}


	/**
	 * Gets the issuer identifier. Corresponds to the {@code issuer}
	 * metadata field.
	 *
	 * @return The issuer identifier.
	 */
	public Issuer getIssuer() {

		return issuer;
	}


	/**
	 * Gets the authorisation endpoint URI. Corresponds the
	 * {@code authorization_endpoint} metadata field.
	 *
	 * @return The authorisation endpoint URI, {@code null} if not
	 *         specified.
	 */
	public URI getAuthorizationEndpointURI() {

		return authzEndpoint;
	}


	/**
	 * Sets the authorisation endpoint URI. Corresponds the
	 * {@code authorization_endpoint} metadata field.
	 *
	 * @param authzEndpoint The authorisation endpoint URI, {@code null} if
	 *                      not specified.
	 */
	public void setAuthorizationEndpointURI(final URI authzEndpoint) {

		this.authzEndpoint = authzEndpoint;
	}


	/**
	 * Gets the token endpoint URI. Corresponds the {@code token_endpoint}
	 * metadata field.
	 *
	 * @return The token endpoint URI, {@code null} if not specified.
	 */
	public URI getTokenEndpointURI() {

		return tokenEndpoint;
	}


	/**
	 * Sts the token endpoint URI. Corresponds the {@code token_endpoint}
	 * metadata field.
	 *
	 * @param tokenEndpoint The token endpoint URI, {@code null} if not
	 *                      specified.
	 */
	public void setTokenEndpointURI(final URI tokenEndpoint) {

		this.tokenEndpoint = tokenEndpoint;
	}


	/**
	 * Gets the client registration endpoint URI. Corresponds to the
	 * {@code registration_endpoint} metadata field.
	 *
	 * @return The client registration endpoint URI, {@code null} if not
	 *         specified.
	 */
	public URI getRegistrationEndpointURI() {

		return regEndpoint;
	}


	/**
	 * Sets the client registration endpoint URI. Corresponds to the
	 * {@code registration_endpoint} metadata field.
	 *
	 * @param regEndpoint The client registration endpoint URI,
	 *                    {@code null} if not specified.
	 */
	public void setRegistrationEndpointURI(final URI regEndpoint) {

		this.regEndpoint = regEndpoint;
	}


	/**
	 * Gets the token introspection endpoint URI. Corresponds to the
	 * {@code introspection_endpoint} metadata field.
	 *
	 * @return The token introspection endpoint URI, {@code null} if not
	 *         specified.
	 */
	public URI getIntrospectionEndpointURI() {

		return introspectionEndpoint;
	}


	/**
	 * Sets the token introspection endpoint URI. Corresponds to the
	 * {@code introspection_endpoint} metadata field.
	 *
	 * @param introspectionEndpoint  The token introspection endpoint URI,
	 *                               {@code null} if not specified.
	 */
	public void setIntrospectionEndpointURI(final URI introspectionEndpoint) {

		this.introspectionEndpoint = introspectionEndpoint;
	}


	/**
	 * Gets the token revocation endpoint URI. Corresponds to the
	 * {@code revocation_endpoint} metadata field.
	 *
	 * @return The token revocation endpoint URI, {@code null} if not
	 *         specified.
	 */
	public URI getRevocationEndpointURI() {

		return revocationEndpoint;
	}


	/**
	 * Sets the token revocation endpoint URI. Corresponds to the
	 * {@code revocation_endpoint} metadata field.
	 *
	 * @param revocationEndpoint The token revocation endpoint URI,
	 *                           {@code null} if not specified.
	 */
	public void setRevocationEndpointURI(final URI revocationEndpoint) {

		this.revocationEndpoint = revocationEndpoint;
	}


	/**
	 * Gets the JSON Web Key (JWK) set URI. Corresponds to the
	 * {@code jwks_uri} metadata field.
	 *
	 * @return The JWK set URI, {@code null} if not specified.
	 */
	public URI getJWKSetURI() {

		return jwkSetURI;
	}


	/**
	 * Sets the JSON Web Key (JWT) set URI. Corresponds to the
	 * {@code jwks_uri} metadata field.
	 *
	 * @param jwkSetURI The JWK set URI, {@code null} if not specified.
	 */
	public void setJWKSetURI(final URI jwkSetURI) {

		this.jwkSetURI = jwkSetURI;
	}


	/**
	 * Gets the supported scope values. Corresponds to the
	 * {@code scopes_supported} metadata field.
	 *
	 * @return The supported scope values, {@code null} if not specified.
	 */
	public Scope getScopes() {

		return scope;
	}


	/**
	 * Sets the supported scope values. Corresponds to the
	 * {@code scopes_supported} metadata field.
	 *
	 * @param scope The supported scope values, {@code null} if not
	 *              specified.
	 */
	public void setScopes(final Scope scope) {

		this.scope = scope;
	}


	/**
	 * Gets the supported response type values. Corresponds to the
	 * {@code response_types_supported} metadata field.
	 *
	 * @return The supported response type values, {@code null} if not
	 *         specified.
	 */
	public List<ResponseType> getResponseTypes() {

		return rts;
	}


	/**
	 * Sets the supported response type values. Corresponds to the
	 * {@code response_types_supported} metadata field.
	 *
	 * @param rts The supported response type values, {@code null} if not
	 *            specified.
	 */
	public void setResponseTypes(final List<ResponseType> rts) {

		this.rts = rts;
	}


	/**
	 * Gets the supported response mode values. Corresponds to the
	 * {@code response_modes_supported}.
	 *
	 * @return The supported response mode values, {@code null} if not
	 *         specified.
	 */
	public List<ResponseMode> getResponseModes() {

		return rms;
	}


	/**
	 * Sets the supported response mode values. Corresponds to the
	 * {@code response_modes_supported}.
	 *
	 * @param rms The supported response mode values, {@code null} if not
	 *            specified.
	 */
	public void setResponseModes(final List<ResponseMode> rms) {

		this.rms = rms;
	}


	/**
	 * Gets the supported OAuth 2.0 grant types. Corresponds to the
	 * {@code grant_types_supported} metadata field.
	 *
	 * @return The supported grant types, {@code null} if not specified.
	 */
	public List<GrantType> getGrantTypes() {

		return gts;
	}


	/**
	 * Sets the supported OAuth 2.0 grant types. Corresponds to the
	 * {@code grant_types_supported} metadata field.
	 *
	 * @param gts The supported grant types, {@code null} if not specified.
	 */
	public void setGrantTypes(final List<GrantType> gts) {

		this.gts = gts;
	}


	/**
	 * Gets the supported authorisation code challenge methods for PKCE.
	 * Corresponds to the {@code code_challenge_methods_supported} metadata
	 * field.
	 *
	 * @return The supported code challenge methods, {@code null} if not
	 *         specified.
	 */
	public List<CodeChallengeMethod> getCodeChallengeMethods() {

		return codeChallengeMethods;
	}


	/**
	 * Gets the supported authorisation code challenge methods for PKCE.
	 * Corresponds to the {@code code_challenge_methods_supported} metadata
	 * field.
	 *
	 * @param codeChallengeMethods The supported code challenge methods,
	 *                             {@code null} if not specified.
	 */
	public void setCodeChallengeMethods(final List<CodeChallengeMethod> codeChallengeMethods) {

		this.codeChallengeMethods = codeChallengeMethods;
	}


	/**
	 * Gets the supported token endpoint authentication methods.
	 * Corresponds to the {@code token_endpoint_auth_methods_supported}
	 * metadata field.
	 *
	 * @return The supported token endpoint authentication methods,
	 *         {@code null} if not specified.
	 */
	public List<ClientAuthenticationMethod> getTokenEndpointAuthMethods() {

		return tokenEndpointAuthMethods;
	}


	/**
	 * Sets the supported token endpoint authentication methods.
	 * Corresponds to the {@code token_endpoint_auth_methods_supported}
	 * metadata field.
	 *
	 * @param authMethods The supported token endpoint authentication
	 *                    methods, {@code null} if not specified.
	 */
	public void setTokenEndpointAuthMethods(final List<ClientAuthenticationMethod> authMethods) {

		this.tokenEndpointAuthMethods = authMethods;
	}


	/**
	 * Gets the supported JWS algorithms for the {@code private_key_jwt}
	 * and {@code client_secret_jwt} token endpoint authentication methods.
	 * Corresponds to the
	 * {@code token_endpoint_auth_signing_alg_values_supported} metadata
	 * field.
	 *
	 * @return The supported JWS algorithms, {@code null} if not specified.
	 */
	public List<JWSAlgorithm> getTokenEndpointJWSAlgs() {

		return tokenEndpointJWSAlgs;
	}


	/**
	 * Sets the supported JWS algorithms for the {@code private_key_jwt}
	 * and {@code client_secret_jwt} token endpoint authentication methods.
	 * Corresponds to the
	 * {@code token_endpoint_auth_signing_alg_values_supported} metadata
	 * field.
	 *
	 * @param jwsAlgs The supported JWS algorithms, {@code null} if not
	 *                specified. Must not contain the {@code none}
	 *                algorithm.
	 */
	public void setTokenEndpointJWSAlgs(final List<JWSAlgorithm> jwsAlgs) {


		this.tokenEndpointJWSAlgs = jwsAlgs;
	}


	/**
	 * Gets the supported introspection endpoint authentication methods.
	 * Corresponds to the
	 * {@code introspection_endpoint_auth_methods_supported} metadata
	 * field.
	 *
	 * @return The supported introspection endpoint authentication methods,
	 *         {@code null} if not specified.
	 */
	public List<ClientAuthenticationMethod> getIntrospectionEndpointAuthMethods() {
		return introspectionEndpointAuthMethods;
	}


	/**
	 * Sets the supported introspection endpoint authentication methods.
	 * Corresponds to the
	 * {@code introspection_endpoint_auth_methods_supported} metadata
	 * field.
	 *
	 * @param authMethods The supported introspection endpoint
	 *                    authentication methods, {@code null} if not
	 *                    specified.
	 */
	public void setIntrospectionEndpointAuthMethods(final List<ClientAuthenticationMethod> authMethods) {

		this.introspectionEndpointAuthMethods = authMethods;
	}


	/**
	 * Gets the supported JWS algorithms for the {@code private_key_jwt}
	 * and {@code client_secret_jwt} introspection endpoint authentication
	 * methods. Corresponds to the
	 * {@code introspection_endpoint_auth_signing_alg_values_supported}
	 * metadata field.
	 *
	 * @return The supported JWS algorithms, {@code null} if not specified.
	 */
	public List<JWSAlgorithm> getIntrospectionEndpointJWSAlgs() {

		return introspectionEndpointJWSAlgs;
	}


	/**
	 * Sets the supported JWS algorithms for the {@code private_key_jwt}
	 * and {@code client_secret_jwt} introspection endpoint authentication
	 * methods. Corresponds to the
	 * {@code introspection_endpoint_auth_signing_alg_values_supported}
	 * metadata field.
	 *
	 * @param jwsAlgs The supported JWS algorithms, {@code null} if not
	 *                specified. Must not contain the {@code none}
	 *                algorithm.
	 */
	public void setIntrospectionEndpointJWSAlgs(final List<JWSAlgorithm> jwsAlgs) {


		introspectionEndpointJWSAlgs = jwsAlgs;
	}


	/**
	 * Gets the supported revocation endpoint authentication methods.
	 * Corresponds to the
	 * {@code revocation_endpoint_auth_methods_supported} metadata field.
	 *
	 * @return The supported revocation endpoint authentication methods,
	 *         {@code null} if not specified.
	 */
	public List<ClientAuthenticationMethod> getRevocationEndpointAuthMethods() {

		return revocationEndpointAuthMethods;
	}


	/**
	 * Sets the supported revocation endpoint authentication methods.
	 * Corresponds to the
	 * {@code revocation_endpoint_auth_methods_supported} metadata field.
	 *
	 * @param authMethods The supported revocation endpoint authentication
	 *                    methods, {@code null} if not specified.
	 */
	public void setRevocationEndpointAuthMethods(final List<ClientAuthenticationMethod> authMethods) {

		revocationEndpointAuthMethods = authMethods;
	}


	/**
	 * Gets the supported JWS algorithms for the {@code private_key_jwt}
	 * and {@code client_secret_jwt} revocation endpoint authentication
	 * methods. Corresponds to the
	 * {@code revocation_endpoint_auth_signing_alg_values_supported}
	 * metadata field.
	 *
	 * @return The supported JWS algorithms, {@code null} if not specified.
	 */
	public List<JWSAlgorithm> getRevocationEndpointJWSAlgs() {

		return revocationEndpointJWSAlgs;
	}


	/**
	 * Sets the supported JWS algorithms for the {@code private_key_jwt}
	 * and {@code client_secret_jwt} revocation endpoint authentication
	 * methods. Corresponds to the
	 * {@code revocation_endpoint_auth_signing_alg_values_supported}
	 * metadata field.
	 *
	 * @param jwsAlgs The supported JWS algorithms, {@code null} if not
	 *                specified. Must not contain the {@code none}
	 *                algorithm.
	 */
	public void setRevocationEndpointJWSAlgs(final List<JWSAlgorithm> jwsAlgs) {

		revocationEndpointJWSAlgs = jwsAlgs;
	}


	/**
	 * Gets the supported JWS algorithms for request objects. Corresponds
	 * to the {@code request_object_signing_alg_values_supported} metadata
	 * field.
	 *
	 * @return The supported JWS algorithms, {@code null} if not specified.
	 */
	public List<JWSAlgorithm> getRequestObjectJWSAlgs() {

		return requestObjectJWSAlgs;
	}


	/**
	 * Sets the supported JWS algorithms for request objects. Corresponds
	 * to the {@code request_object_signing_alg_values_supported} metadata
	 * field.
	 *
	 * @param requestObjectJWSAlgs The supported JWS algorithms,
	 *                             {@code null} if not specified.
	 */
	public void setRequestObjectJWSAlgs(final List<JWSAlgorithm> requestObjectJWSAlgs) {

		this.requestObjectJWSAlgs = requestObjectJWSAlgs;
	}


	/**
	 * Gets the supported JWE algorithms for request objects. Corresponds
	 * to the {@code request_object_encryption_alg_values_supported}
	 * metadata field.
	 *
	 * @return The supported JWE algorithms, {@code null} if not specified.
	 */
	public List<JWEAlgorithm> getRequestObjectJWEAlgs() {

		return requestObjectJWEAlgs;
	}


	/**
	 * Sets the supported JWE algorithms for request objects. Corresponds
	 * to the {@code request_object_encryption_alg_values_supported}
	 * metadata field.
	 *
	 * @param requestObjectJWEAlgs The supported JWE algorithms,
	 *                            {@code null} if not specified.
	 */
	public void setRequestObjectJWEAlgs(final List<JWEAlgorithm> requestObjectJWEAlgs) {

		this.requestObjectJWEAlgs = requestObjectJWEAlgs;
	}


	/**
	 * Gets the supported encryption methods for request objects.
	 * Corresponds to the
	 * {@code request_object_encryption_enc_values_supported} metadata
	 * field.
	 *
	 * @return The supported encryption methods, {@code null} if not
	 *         specified.
	 */
	public List<EncryptionMethod> getRequestObjectJWEEncs() {

		return requestObjectJWEEncs;
	}


	/**
	 * Sets the supported encryption methods for request objects.
	 * Corresponds to the
	 * {@code request_object_encryption_enc_values_supported} metadata
	 * field.
	 *
	 * @param requestObjectJWEEncs The supported encryption methods,
	 *                             {@code null} if not specified.
	 */
	public void setRequestObjectJWEEncs(final List<EncryptionMethod> requestObjectJWEEncs) {

		this.requestObjectJWEEncs = requestObjectJWEEncs;
	}


	/**
	 * Gets the support for the {@code request} authorisation request
	 * parameter. Corresponds to the {@code request_parameter_supported}
	 * metadata field.
	 *
	 * @return {@code true} if the {@code reqeust} parameter is supported,
	 *         else {@code false}.
	 */
	public boolean supportsRequestParam() {

		return requestParamSupported;
	}


	/**
	 * Sets the support for the {@code request} authorisation request
	 * parameter. Corresponds to the {@code request_parameter_supported}
	 * metadata field.
	 *
	 * @param requestParamSupported {@code true} if the {@code reqeust}
	 *                              parameter is supported, else
	 *                              {@code false}.
	 */
	public void setSupportsRequestParam(final boolean requestParamSupported) {

		this.requestParamSupported = requestParamSupported;
	}


	/**
	 * Gets the support for the {@code request_uri} authorisation request
	 * parameter. Corresponds the {@code request_uri_parameter_supported}
	 * metadata field.
	 *
	 * @return {@code true} if the {@code request_uri} parameter is
	 *         supported, else {@code false}.
	 */
	public boolean supportsRequestURIParam() {

		return requestURIParamSupported;
	}


	/**
	 * Sets the support for the {@code request_uri} authorisation request
	 * parameter. Corresponds the {@code request_uri_parameter_supported}
	 * metadata field.
	 *
	 * @param requestURIParamSupported {@code true} if the
	 *                                 {@code request_uri} parameter is
	 *                                 supported, else {@code false}.
	 */
	public void setSupportsRequestURIParam(final boolean requestURIParamSupported) {

		this.requestURIParamSupported = requestURIParamSupported;
	}


	/**
	 * Gets the requirement for the {@code request_uri} parameter
	 * pre-registration. Corresponds to the
	 * {@code require_request_uri_registration} metadata field.
	 *
	 * @return {@code true} if the {@code request_uri} parameter values
	 *         must be pre-registered, else {@code false}.
	 */
	public boolean requiresRequestURIRegistration() {

		return requireRequestURIReg;
	}


	/**
	 * Sets the requirement for the {@code request_uri} parameter
	 * pre-registration. Corresponds to the
	 * {@code require_request_uri_registration} metadata field.
	 *
	 * @param requireRequestURIReg {@code true} if the {@code request_uri}
	 *                             parameter values must be pre-registered,
	 *                             else {@code false}.
	 */
	public void setRequiresRequestURIRegistration(final boolean requireRequestURIReg) {

		this.requireRequestURIReg = requireRequestURIReg;
	}


	/**
	 * Gets the supported UI locales. Corresponds to the
	 * {@code ui_locales_supported} metadata field.
	 *
	 * @return The supported UI locales, {@code null} if not specified.
	 */
	public List<LangTag> getUILocales() {

		return uiLocales;
	}


	/**
	 * Sets the supported UI locales. Corresponds to the
	 * {@code ui_locales_supported} metadata field.
	 *
	 * @param uiLocales The supported UI locales, {@code null} if not
	 *                  specified.
	 */
	public void setUILocales(final List<LangTag> uiLocales) {

		this.uiLocales = uiLocales;
	}


	/**
	 * Gets the service documentation URI. Corresponds to the
	 * {@code service_documentation} metadata field.
	 *
	 * @return The service documentation URI, {@code null} if not
	 *         specified.
	 */
	public URI getServiceDocsURI() {

		return serviceDocsURI;
	}


	/**
	 * Sets the service documentation URI. Corresponds to the
	 * {@code service_documentation} metadata field.
	 *
	 * @param serviceDocsURI The service documentation URI, {@code null} if
	 *                       not specified.
	 */
	public void setServiceDocsURI(final URI serviceDocsURI) {

		this.serviceDocsURI = serviceDocsURI;
	}


	/**
	 * Gets the provider's policy regarding relying party use of data.
	 * Corresponds to the {@code op_policy_uri} metadata field.
	 *
	 * @return The policy URI, {@code null} if not specified.
	 */
	public URI getPolicyURI() {

		return policyURI;
	}


	/**
	 * Sets the provider's policy regarding relying party use of data.
	 * Corresponds to the {@code op_policy_uri} metadata field.
	 *
	 * @param policyURI The policy URI, {@code null} if not specified.
	 */
	public void setPolicyURI(final URI policyURI) {

		this.policyURI = policyURI;
	}


	/**
	 * Gets the provider's terms of service. Corresponds to the
	 * {@code op_tos_uri} metadata field.
	 *
	 * @return The terms of service URI, {@code null} if not specified.
	 */
	public URI getTermsOfServiceURI() {

		return tosURI;
	}


	/**
	 * Sets the provider's terms of service. Corresponds to the
	 * {@code op_tos_uri} metadata field.
	 *
	 * @param tosURI The terms of service URI, {@code null} if not
	 *               specified.
	 */
	public void setTermsOfServiceURI(final URI tosURI) {

		this.tosURI = tosURI;
	}


	/**
	 * Gets the support for TLS client certificate bound access tokens.
	 * Corresponds to the
	 * {@code tls_client_certificate_bound_access_tokens} metadata field.
	 *
	 * @return {@code true} if TLS client certificate bound access tokens
	 *         are supported, else {@code false}.
	 */
	public boolean supportsTLSClientCertificateBoundAccessTokens() {

		return tlsClientCertificateBoundAccessTokens;
	}


	/**
	 * Sets the support for TLS client certificate bound access tokens.
	 * Corresponds to the
	 * {@code tls_client_certificate_bound_access_tokens} metadata field.
	 *
	 * @param tlsClientCertBoundTokens {@code true} if TLS client
	 *                                 certificate bound access tokens are
	 *                                 supported, else {@code false}.
	 */
	public void setSupportsTLSClientCertificateBoundAccessTokens(final boolean tlsClientCertBoundTokens) {

		tlsClientCertificateBoundAccessTokens = tlsClientCertBoundTokens;
	}


	/**
	 * Gets the support for TLS client certificate bound access tokens.
	 * Corresponds to the
	 * {@code tls_client_certificate_bound_access_tokens} metadata field.
	 *
	 * @return {@code true} if TLS client certificate bound access tokens
	 *         are supported, else {@code false}.
	 */
	@Deprecated
	public boolean supportsMutualTLSSenderConstrainedAccessTokens() {

		return supportsTLSClientCertificateBoundAccessTokens();
	}


	/**
	 * Sets the support for TLS client certificate bound access tokens.
	 * Corresponds to the
	 * {@code tls_client_certificate_bound_access_tokens} metadata field.
	 *
	 * @param mutualTLSSenderConstrainedAccessTokens {@code true} if TLS
	 *                                               client certificate
	 *                                               bound access tokens
	 *                                               are supported, else
	 *                                               {@code false}.
	 */
	@Deprecated
	public void setSupportsMutualTLSSenderConstrainedAccessTokens(final boolean mutualTLSSenderConstrainedAccessTokens) {

		setSupportsTLSClientCertificateBoundAccessTokens(mutualTLSSenderConstrainedAccessTokens);
	}


	/**
	 * Gets the specified custom (not registered) parameter.
	 *
	 * @param name The parameter name. Must not be {@code null}.
	 *
	 * @return The parameter value, {@code null} if not specified.
	 */
	public Object getCustomParameter(final String name) {

		return customParameters.get(name);
	}


	/**
	 * Gets the specified custom (not registered) URI parameter.
	 *
	 * @param name The parameter name. Must not be {@code null}.
	 *
	 * @return The parameter URI value, {@code null} if not specified.
	 */
	public URI getCustomURIParameter(final String name) {

		try {
			return JSONObjectUtils.getURI(customParameters, name);
		} catch (ParseException e) {
			return null;
		}
	}


	/**
	 * Sets the specified custom (not registered) parameter.
	 *
	 * @param name  The parameter name. Must not be {@code null}.
	 * @param value The parameter value, {@code null} if not specified.
	 */
	public void setCustomParameter(final String name, final Object value) {

		if (REGISTERED_PARAMETER_NAMES.contains(name)) {
			throw new IllegalArgumentException("The " + name + " parameter is registered");
		}

		customParameters.put(name, value);
	}


	/**
	 * Gets the custom (not registered) parameters.
	 *
	 * @return The custom parameters, empty JSON object if none.
	 */
	public JSONObject getCustomParameters() {

		return customParameters;
	}


	/**
	 * Applies the OAuth 2.0 Authorisation Server metadata defaults where
	 * no values have been specified.
	 *
	 * <ul>
	 *     <li>The response modes default to {@code ["query", "fragment"]}.
	 *     <li>The grant types default to {@code ["authorization_code",
	 *         "implicit"]}.
	 *     <li>The token endpoint authentication methods default to
	 *         {@code ["client_secret_basic"]}.
	 * </ul>
	 */
	public void applyDefaults() {

		if (rms == null) {
			rms = new ArrayList<>(2);
			rms.add(ResponseMode.QUERY);
			rms.add(ResponseMode.FRAGMENT);
		}

		if (gts == null) {
			gts = new ArrayList<>(2);
			gts.add(GrantType.AUTHORIZATION_CODE);
			gts.add(GrantType.IMPLICIT);
		}

		if (tokenEndpointAuthMethods == null) {
			tokenEndpointAuthMethods = new ArrayList<>();
			tokenEndpointAuthMethods.add(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
		}
	}


	/**
	 * Returns the JSON object representation of this OpenID Connect
	 * provider metadata.
	 *
	 * @return The JSON object representation.
	 */
	public JSONObject toJSONObject() {

		JSONObject o = new OrderedJSONObject();

		// Mandatory fields
		o.put("issuer", issuer.getValue());


		// Optional fields
		if (jwkSetURI != null)
			o.put("jwks_uri", jwkSetURI.toString());

		if (authzEndpoint != null)
			o.put("authorization_endpoint", authzEndpoint.toString());

		if (tokenEndpoint != null)
			o.put("token_endpoint", tokenEndpoint.toString());

		if (regEndpoint != null)
			o.put("registration_endpoint", regEndpoint.toString());

		if (introspectionEndpoint != null)
			o.put("introspection_endpoint", introspectionEndpoint.toString());

		if (revocationEndpoint != null)
			o.put("revocation_endpoint", revocationEndpoint.toString());

		if (scope != null)
			o.put("scopes_supported", scope.toStringList());

		List<String> stringList;

		if (rts != null) {

			stringList = new ArrayList<>(rts.size());

			for (ResponseType rt: rts)
				stringList.add(rt.toString());

			o.put("response_types_supported", stringList);
		}

		if (rms != null) {

			stringList = new ArrayList<>(rms.size());

			for (ResponseMode rm: rms)
				stringList.add(rm.getValue());

			o.put("response_modes_supported", stringList);
		}

		if (gts != null) {

			stringList = new ArrayList<>(gts.size());

			for (GrantType gt: gts)
				stringList.add(gt.toString());

			o.put("grant_types_supported", stringList);
		}

		if (codeChallengeMethods != null) {

			stringList = new ArrayList<>(codeChallengeMethods.size());

			for (CodeChallengeMethod m: codeChallengeMethods)
				stringList.add(m.getValue());

			o.put("code_challenge_methods_supported", stringList);
		}


		if (tokenEndpointAuthMethods != null) {

			stringList = new ArrayList<>(tokenEndpointAuthMethods.size());

			for (ClientAuthenticationMethod m: tokenEndpointAuthMethods)
				stringList.add(m.getValue());

			o.put("token_endpoint_auth_methods_supported", stringList);
		}

		if (tokenEndpointJWSAlgs != null) {

			stringList = new ArrayList<>(tokenEndpointJWSAlgs.size());

			for (JWSAlgorithm alg: tokenEndpointJWSAlgs)
				stringList.add(alg.getName());

			o.put("token_endpoint_auth_signing_alg_values_supported", stringList);
		}

		if (introspectionEndpointAuthMethods != null) {

			stringList = new ArrayList<>(introspectionEndpointAuthMethods.size());

			for (ClientAuthenticationMethod m: introspectionEndpointAuthMethods)
				stringList.add(m.getValue());

			o.put("introspection_endpoint_auth_methods_supported", stringList);
		}

		if (introspectionEndpointJWSAlgs != null) {

			stringList = new ArrayList<>(introspectionEndpointJWSAlgs.size());

			for (JWSAlgorithm alg: introspectionEndpointJWSAlgs)
				stringList.add(alg.getName());

			o.put("introspection_endpoint_auth_signing_alg_values_supported", stringList);
		}

		if (revocationEndpointAuthMethods != null) {

			stringList = new ArrayList<>(revocationEndpointAuthMethods.size());

			for (ClientAuthenticationMethod m: revocationEndpointAuthMethods)
				stringList.add(m.getValue());

			o.put("revocation_endpoint_auth_methods_supported", stringList);
		}

		if (revocationEndpointJWSAlgs != null) {

			stringList = new ArrayList<>(revocationEndpointJWSAlgs.size());

			for (JWSAlgorithm alg: revocationEndpointJWSAlgs)
				stringList.add(alg.getName());

			o.put("revocation_endpoint_auth_signing_alg_values_supported", stringList);
		}

		if (requestObjectJWSAlgs != null) {

			stringList = new ArrayList<>(requestObjectJWSAlgs.size());

			for (JWSAlgorithm alg: requestObjectJWSAlgs)
				stringList.add(alg.getName());

			o.put("request_object_signing_alg_values_supported", stringList);
		}

		if (requestObjectJWEAlgs != null) {

			stringList = new ArrayList<>(requestObjectJWEAlgs.size());

			for (JWEAlgorithm alg: requestObjectJWEAlgs)
				stringList.add(alg.getName());

			o.put("request_object_encryption_alg_values_supported", stringList);
		}

		if (requestObjectJWEEncs != null) {

			stringList = new ArrayList<>(requestObjectJWEEncs.size());

			for (EncryptionMethod m: requestObjectJWEEncs)
				stringList.add(m.getName());

			o.put("request_object_encryption_enc_values_supported", stringList);
		}

		if (uiLocales != null) {

			stringList = new ArrayList<>(uiLocales.size());

			for (LangTag l: uiLocales)
				stringList.add(l.toString());

			o.put("ui_locales_supported", stringList);
		}

		if (serviceDocsURI != null)
			o.put("service_documentation", serviceDocsURI.toString());

		if (policyURI != null)
			o.put("op_policy_uri", policyURI.toString());

		if (tosURI != null)
			o.put("op_tos_uri", tosURI.toString());

		o.put("request_parameter_supported", requestParamSupported);
		o.put("request_uri_parameter_supported", requestURIParamSupported);
		o.put("require_request_uri_registration", requireRequestURIReg);

		o.put("tls_client_certificate_bound_access_tokens", tlsClientCertificateBoundAccessTokens);

		// Append any custom (not registered) parameters
		o.putAll(customParameters);

		return o;
	}


	@Override
	public String toString() {
		return toJSONObject().toJSONString();
	}


	/**
	 * Parses an OAuth 2.0 Authorisation Server metadata from the specified
	 * JSON object.
	 *
	 * @param jsonObject The JSON object to parse. Must not be
	 *                   {@code null}.
	 *
	 * @return The OAuth 2.0 Authorisation Server metadata.
	 *
	 * @throws ParseException If the JSON object couldn't be parsed to an
	 *                        OAuth 2.0 Authorisation Server metadata.
	 */
	public static UnsafeAuthorizationServerMetadata parse(final JSONObject jsonObject)
			throws ParseException {

		// Parse issuer and subject_types_supported first

		Issuer issuer = new Issuer(JSONObjectUtils.getURI(jsonObject, "issuer").toString());

		UnsafeAuthorizationServerMetadata as;

		try {
			as = new UnsafeAuthorizationServerMetadata(issuer); // validates issuer syntax
		} catch (IllegalArgumentException e) {
			throw new ParseException(e.getMessage(), e);
		}

		// Endpoints
		if (jsonObject.get("authorization_endpoint") != null)
			as.authzEndpoint = JSONObjectUtils.getURI(jsonObject, "authorization_endpoint");

		if (jsonObject.get("token_endpoint") != null)
			as.tokenEndpoint = JSONObjectUtils.getURI(jsonObject, "token_endpoint");

		if (jsonObject.get("registration_endpoint") != null)
			as.regEndpoint = JSONObjectUtils.getURI(jsonObject, "registration_endpoint");

		if (jsonObject.get("jwks_uri") != null)
			as.jwkSetURI = JSONObjectUtils.getURI(jsonObject, "jwks_uri");

		if (jsonObject.get("introspection_endpoint") != null)
			as.introspectionEndpoint = JSONObjectUtils.getURI(jsonObject, "introspection_endpoint");

		if (jsonObject.get("revocation_endpoint") != null)
			as.revocationEndpoint = JSONObjectUtils.getURI(jsonObject, "revocation_endpoint");

		// AS capabilities
		if (jsonObject.get("scopes_supported") != null) {

			as.scope = new Scope();

			for (String v: JSONObjectUtils.getStringArray(jsonObject, "scopes_supported")) {

				if (v != null)
					as.scope.add(new Scope.Value(v));
			}
		}

		if (jsonObject.get("response_types_supported") != null) {

			as.rts = new ArrayList<>();

			for (String v: JSONObjectUtils.getStringArray(jsonObject, "response_types_supported")) {

				if (v != null)
					as.rts.add(ResponseType.parse(v));
			}
		}

		if (jsonObject.get("response_modes_supported") != null) {

			as.rms = new ArrayList<>();

			for (String v: JSONObjectUtils.getStringArray(jsonObject, "response_modes_supported")) {

				if (v != null)
					as.rms.add(new ResponseMode(v));
			}
		}

		if (jsonObject.get("grant_types_supported") != null) {

			as.gts = new ArrayList<>();

			for (String v: JSONObjectUtils.getStringArray(jsonObject, "grant_types_supported")) {

				if (v != null)
					as.gts.add(GrantType.parse(v));
			}
		}

		if (jsonObject.get("code_challenge_methods_supported") != null) {

			as.codeChallengeMethods = new ArrayList<>();

			for (String v: JSONObjectUtils.getStringArray(jsonObject, "code_challenge_methods_supported")) {

				if (v != null)
					as.codeChallengeMethods.add(CodeChallengeMethod.parse(v));
			}
		}

		if (jsonObject.get("token_endpoint_auth_methods_supported") != null) {

			as.tokenEndpointAuthMethods = new ArrayList<>();

			for (String v: JSONObjectUtils.getStringArray(jsonObject, "token_endpoint_auth_methods_supported")) {

				if (v != null)
					as.tokenEndpointAuthMethods.add(ClientAuthenticationMethod.parse(v));
			}
		}

		if (jsonObject.get("token_endpoint_auth_signing_alg_values_supported") != null) {

			as.tokenEndpointJWSAlgs = new ArrayList<>();

			for (String v: JSONObjectUtils.getStringArray(jsonObject, "token_endpoint_auth_signing_alg_values_supported")) {


				if (v != null)
					as.tokenEndpointJWSAlgs.add(JWSAlgorithm.parse(v));
			}
		}

		if (jsonObject.get("introspection_endpoint_auth_methods_supported") != null) {

			as.introspectionEndpointAuthMethods = new ArrayList<>();

			for (String v: JSONObjectUtils.getStringArray(jsonObject, "introspection_endpoint_auth_methods_supported")) {

				if (v != null)
					as.introspectionEndpointAuthMethods.add(ClientAuthenticationMethod.parse(v));
			}
		}

		if (jsonObject.get("introspection_endpoint_auth_signing_alg_values_supported") != null) {

			as.introspectionEndpointJWSAlgs = new ArrayList<>();

			for (String v: JSONObjectUtils.getStringArray(jsonObject, "introspection_endpoint_auth_signing_alg_values_supported")) {


				if (v != null)
					as.introspectionEndpointJWSAlgs.add(JWSAlgorithm.parse(v));
			}
		}

		if (jsonObject.get("revocation_endpoint_auth_methods_supported") != null) {

			as.revocationEndpointAuthMethods = new ArrayList<>();

			for (String v: JSONObjectUtils.getStringArray(jsonObject, "revocation_endpoint_auth_methods_supported")) {

				if (v != null)
					as.revocationEndpointAuthMethods.add(ClientAuthenticationMethod.parse(v));
			}
		}

		if (jsonObject.get("revocation_endpoint_auth_signing_alg_values_supported") != null) {

			as.revocationEndpointJWSAlgs = new ArrayList<>();

			for (String v: JSONObjectUtils.getStringArray(jsonObject, "revocation_endpoint_auth_signing_alg_values_supported")) {
				
				if (v != null)
					as.revocationEndpointJWSAlgs.add(JWSAlgorithm.parse(v));
			}
		}


		// Request object

		if (jsonObject.get("request_object_signing_alg_values_supported") != null) {

			as.requestObjectJWSAlgs = new ArrayList<>();

			for (String v: JSONObjectUtils.getStringArray(jsonObject, "request_object_signing_alg_values_supported")) {

				if (v != null)
					as.requestObjectJWSAlgs.add(JWSAlgorithm.parse(v));
			}
		}


		if (jsonObject.get("request_object_encryption_alg_values_supported") != null) {

			as.requestObjectJWEAlgs = new ArrayList<>();

			for (String v: JSONObjectUtils.getStringArray(jsonObject, "request_object_encryption_alg_values_supported")) {

				if (v != null)
					as.requestObjectJWEAlgs.add(JWEAlgorithm.parse(v));
			}
		}


		if (jsonObject.get("request_object_encryption_enc_values_supported") != null) {

			as.requestObjectJWEEncs = new ArrayList<>();

			for (String v: JSONObjectUtils.getStringArray(jsonObject, "request_object_encryption_enc_values_supported")) {

				if (v != null)
					as.requestObjectJWEEncs.add(EncryptionMethod.parse(v));
			}
		}


		// Misc

		if (jsonObject.get("ui_locales_supported") != null) {

			as.uiLocales = new ArrayList<>();

			for (String v : JSONObjectUtils.getStringArray(jsonObject, "ui_locales_supported")) {

				if (v != null) {

					try {
						as.uiLocales.add(LangTag.parse(v));

					} catch (LangTagException e) {

						throw new ParseException("Invalid ui_locales_supported field: " + e.getMessage(), e);
					}
				}
			}
		}

		if (jsonObject.get("service_documentation") != null)
			as.serviceDocsURI = JSONObjectUtils.getURI(jsonObject, "service_documentation");

		if (jsonObject.get("op_policy_uri") != null)
			as.policyURI = JSONObjectUtils.getURI(jsonObject, "op_policy_uri");

		if (jsonObject.get("op_tos_uri") != null)
			as.tosURI = JSONObjectUtils.getURI(jsonObject, "op_tos_uri");

		if (jsonObject.get("request_parameter_supported") != null)
			as.requestParamSupported = JSONObjectUtils.getBoolean(jsonObject, "request_parameter_supported");

		if (jsonObject.get("request_uri_parameter_supported") != null)
			as.requestURIParamSupported = JSONObjectUtils.getBoolean(jsonObject, "request_uri_parameter_supported");

		if (jsonObject.get("require_request_uri_registration") != null)
			as.requireRequestURIReg = JSONObjectUtils.getBoolean(jsonObject, "require_request_uri_registration");

		if (jsonObject.get("tls_client_certificate_bound_access_tokens") != null)
			as.tlsClientCertificateBoundAccessTokens = JSONObjectUtils.getBoolean(jsonObject, "tls_client_certificate_bound_access_tokens");

		// Parse custom (not registered) parameters
		JSONObject customParams = new JSONObject(jsonObject);
		customParams.keySet().removeAll(REGISTERED_PARAMETER_NAMES);
		for (Map.Entry<String,Object> customEntry: customParams.entrySet()) {
			as.setCustomParameter(customEntry.getKey(), customEntry.getValue());
		}

		return as;
	}


	/**
	 * Parses an OAuth 2.0 Authorisation Server metadata from the specified
	 * JSON object string.
	 *
	 * @param s The JSON object sting to parse. Must not be {@code null}.
	 *
	 * @return The OAuth 2.0 Authorisation Server metadata.
	 *
	 * @throws ParseException If the JSON object string couldn't be parsed
	 *                        to an OAuth 2.0 Authorisation Server
	 *                        metadata.
	 */
	public static UnsafeAuthorizationServerMetadata parse(final String s)
			throws ParseException {

		return parse(JSONObjectUtils.parse(s));
	}


	/**
	 * Resolves OAuth 2.0 authorisation server metadata from the specified
	 * issuer identifier. The metadata is downloaded by HTTP GET from
	 * {@code [issuer-url]/.well-known/oauth-authorization-server}.
	 *
	 * @param issuer The issuer identifier. Must represent a valid HTTPS or
	 *               HTTP URL. Must not be {@code null}.
	 *
	 * @return The OAuth 2.0 authorisation server metadata.
	 *
	 * @throws GeneralException If the issuer identifier or the downloaded
	 *                          metadata are invalid.
	 * @throws IOException      On a HTTP exception.
	 */
	public static UnsafeAuthorizationServerMetadata resolve(final Issuer issuer)
			throws GeneralException, IOException {

		return resolve(issuer, 0, 0);
	}


	/**
	 * Resolves OAuth 2.0 authorisation server metadata from the specified
	 * issuer identifier. The metadata is downloaded by HTTP GET from
	 * {@code [issuer-url]/.well-known/oauth-authorization-server}.
	 *
	 * @param issuer         The issuer identifier. Must represent a valid
	 *                       HTTPS or HTTP URL. Must not be {@code null}.
	 * @param connectTimeout The HTTP connect timeout, in milliseconds.
	 *                       Zero implies no timeout. Must not be negative.
	 * @param readTimeout    The HTTP response read timeout, in
	 *                       milliseconds. Zero implies no timeout. Must
	 *                       not be negative.
	 *
	 * @return The OAuth 2.0 authorisation server metadata.
	 *
	 * @throws GeneralException If the issuer identifier or the downloaded
	 *                          metadata are invalid.
	 * @throws IOException      On a HTTP exception.
	 */
	public static UnsafeAuthorizationServerMetadata resolve(final Issuer issuer,
													  final int connectTimeout,
													  final int readTimeout)
			throws GeneralException, IOException {

		URL configURL;

		try {
			URL issuerURL = new URL(issuer.getValue());

			// Validate but don't insist on HTTPS
			if (issuerURL.getQuery() != null && ! issuerURL.getQuery().trim().isEmpty()) {
				throw new GeneralException("The issuer identifier must not contain a query component");
			}

			if (issuerURL.getPath() != null && issuerURL.getPath().endsWith("/")) {
				configURL = new URL(issuerURL + ".well-known/oauth-authorization-server");
			} else {
				configURL = new URL(issuerURL + "/.well-known/oauth-authorization-server");
			}

		} catch (MalformedURLException e) {
			throw new GeneralException("The issuer identifier doesn't represent a valid URL: " + e.getMessage(), e);
		}

		HTTPRequest httpRequest = new HTTPRequest(HTTPRequest.Method.GET, configURL);
		httpRequest.setConnectTimeout(connectTimeout);
		httpRequest.setReadTimeout(readTimeout);

		HTTPResponse httpResponse = httpRequest.send();

		if (httpResponse.getStatusCode() != 200) {
			throw new IOException("Couldn't download OAuth 2.0 Authorization Server metadata from " + configURL +
					": Status code " + httpResponse.getStatusCode());
		}

		JSONObject jsonObject = httpResponse.getContentAsJSONObject();

		UnsafeAuthorizationServerMetadata as = UnsafeAuthorizationServerMetadata.parse(jsonObject);

		if (! issuer.equals(as.issuer)) {
			throw new GeneralException("The returned issuer doesn't match the expected: " + as.getIssuer());
		}

		return as;
	}
}
