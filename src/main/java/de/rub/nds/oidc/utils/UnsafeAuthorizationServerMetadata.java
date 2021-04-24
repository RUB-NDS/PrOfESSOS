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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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

		if (uri.getRawQuery() != null) {
			throw new IllegalArgumentException("The issuer URI must be without a query component");
		}

		if (uri.getRawFragment() != null) {
			throw new IllegalArgumentException("The issuer URI must be without a fragment component");
		}

		this.issuer = issuer;
	}


	protected static <T extends Object> Function<T, String> mapToString() {
		return (val) -> val.toString();
	}

	protected static <T> Function<List<T>, List<String>> mapToStringList(Function<T, String> converter) {
		return (list) -> {
			return list.stream().map(converter).collect(Collectors.toList());
		};
	}

	protected static <T> void putIfNonnull(JSONObject o, String key, T val) {
		putIfNonnull(o, key, val, mapToString());
	}

	protected static <T> void putIfNonnull(JSONObject o, String key, T val, Function<T, ?> converter) {
		if (val != null) {
			o.put(key, converter.apply(val));
		}
	}


	protected static interface JsonExtractor <T> {
		T extract(JSONObject jsonObject, String key) throws ParseException;
	}

	protected static final JsonExtractor<URI> readJsonUri = (j, k) -> JSONObjectUtils.getURI(j, k);
	protected static final JsonExtractor<Boolean> readJsonBool = (j, k) -> JSONObjectUtils.getBoolean(j, k);


	protected static interface JsonValueConverter <T> {
		T convert(String value) throws ParseException;
	}

	protected static <T> T extractFromJson(JSONObject jsonObject, String key, JsonExtractor<T> extractor) throws ParseException {
		return extractFromJson(jsonObject, key, extractor, null);
	}

	protected static <T> T extractFromJson(JSONObject jsonObject, String key, JsonExtractor<T> extractor, T defaultVal) throws ParseException {
		if (jsonObject.get(key) != null) {
			return extractor.extract(jsonObject, key);
		} else {
			return defaultVal;
		}
	}

	protected static <V, T extends Collection<V>> JsonExtractor<T> extractNestedAdd(Supplier<T> constructor, JsonValueConverter<V> converter) {
		return (j, k) -> {
			T result = constructor.get();

			var sublist = JSONObjectUtils.getStringList(j, k);
			for (String next : sublist) {
				var v = converter.convert(next);
				result.add(v);
			}

			return result;
		};
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
		putIfNonnull(o, "jwks_uri", jwkSetURI);
		putIfNonnull(o, "authorization_endpoint", authzEndpoint);
		putIfNonnull(o, "token_endpoint", tokenEndpoint);
		putIfNonnull(o, "registration_endpoint", regEndpoint);
		putIfNonnull(o, "introspection_endpoint", introspectionEndpoint);
		putIfNonnull(o, "revocation_endpoint", revocationEndpoint);
		putIfNonnull(o, "scopes_supported", scope, Scope::toStringList);

		putIfNonnull(o, "response_types_supported", rts, mapToStringList(ResponseType::toString));
		putIfNonnull(o, "grant_types_supported", rms, mapToStringList(ResponseMode::getValue));
		putIfNonnull(o, "code_challenge_methods_supported", codeChallengeMethods, mapToStringList(CodeChallengeMethod::getValue));
		putIfNonnull(o, "token_endpoint_auth_methods_supported", tokenEndpointAuthMethods, mapToStringList(ClientAuthenticationMethod::getValue));
		putIfNonnull(o, "token_endpoint_auth_signing_alg_values_supported", tokenEndpointJWSAlgs, mapToStringList(JWSAlgorithm::getName));
		putIfNonnull(o, "introspection_endpoint_auth_methods_supported", introspectionEndpointAuthMethods, mapToStringList(ClientAuthenticationMethod::getValue));
		putIfNonnull(o, "introspection_endpoint_auth_signing_alg_values_supported", introspectionEndpointJWSAlgs, mapToStringList(JWSAlgorithm::getName));
		putIfNonnull(o, "revocation_endpoint_auth_methods_supported", revocationEndpointAuthMethods, mapToStringList(ClientAuthenticationMethod::getValue));
		putIfNonnull(o, "revocation_endpoint_auth_signing_alg_values_supported", revocationEndpointJWSAlgs, mapToStringList(JWSAlgorithm::getName));
		putIfNonnull(o, "request_object_signing_alg_values_supported", requestObjectJWSAlgs, mapToStringList(JWSAlgorithm::getName));
		putIfNonnull(o, "request_object_encryption_alg_values_supported", requestObjectJWEAlgs, mapToStringList(JWEAlgorithm::getName));
		putIfNonnull(o, "request_object_encryption_enc_values_supported", requestObjectJWEEncs, mapToStringList(EncryptionMethod::getName));
		putIfNonnull(o, "ui_locales_supported", uiLocales, mapToStringList(LangTag::toString));
		putIfNonnull(o, "service_documentation", serviceDocsURI);
		putIfNonnull(o, "op_policy_uri", policyURI);
		putIfNonnull(o, "op_tos_uri", tosURI);

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
		as.authzEndpoint = extractFromJson(jsonObject, "authorization_endpoint", readJsonUri);
		as.tokenEndpoint = extractFromJson(jsonObject, "token_endpoint", readJsonUri);
		as.regEndpoint = extractFromJson(jsonObject, "registration_endpoint", readJsonUri);
		as.jwkSetURI = extractFromJson(jsonObject, "jwks_uri", readJsonUri);
		as.introspectionEndpoint = extractFromJson(jsonObject, "introspection_endpoint", readJsonUri);
		as.revocationEndpoint = extractFromJson(jsonObject, "revocation_endpoint", readJsonUri);

		// AS capabilities
		as.scope = extractFromJson(jsonObject, "scopes_supported", extractNestedAdd(Scope::new, Scope.Value::new));
		as.rts = extractFromJson(jsonObject, "response_types_supported", extractNestedAdd(ArrayList::new, ResponseType::parse));
		as.rms = extractFromJson(jsonObject, "response_modes_supported", extractNestedAdd(ArrayList::new, ResponseMode::new));
		as.gts = extractFromJson(jsonObject, "grant_types_supported", extractNestedAdd(ArrayList::new, GrantType::parse));
		as.codeChallengeMethods = extractFromJson(jsonObject, "code_challenge_methods_supported", extractNestedAdd(ArrayList::new, CodeChallengeMethod::parse));
		as.tokenEndpointAuthMethods = extractFromJson(jsonObject, "token_endpoint_auth_methods_supported", extractNestedAdd(ArrayList::new, ClientAuthenticationMethod::parse));
		as.tokenEndpointJWSAlgs = extractFromJson(jsonObject, "token_endpoint_auth_signing_alg_values_supported", extractNestedAdd(ArrayList::new, JWSAlgorithm::parse));
		as.introspectionEndpointAuthMethods = extractFromJson(jsonObject, "introspection_endpoint_auth_methods_supported", extractNestedAdd(ArrayList::new, ClientAuthenticationMethod::parse));
		as.introspectionEndpointJWSAlgs = extractFromJson(jsonObject, "introspection_endpoint_auth_signing_alg_values_supported", extractNestedAdd(ArrayList::new, JWSAlgorithm::parse));
		as.revocationEndpointAuthMethods = extractFromJson(jsonObject, "revocation_endpoint_auth_methods_supported", extractNestedAdd(ArrayList::new, ClientAuthenticationMethod::parse));
		as.revocationEndpointJWSAlgs = extractFromJson(jsonObject, "revocation_endpoint_auth_signing_alg_values_supported", extractNestedAdd(ArrayList::new, JWSAlgorithm::parse));

		// Request object
		as.requestObjectJWSAlgs = extractFromJson(jsonObject, "request_object_signing_alg_values_supported", extractNestedAdd(ArrayList::new, JWSAlgorithm::parse));
		as.requestObjectJWEAlgs = extractFromJson(jsonObject, "request_object_encryption_alg_values_supported", extractNestedAdd(ArrayList::new, JWEAlgorithm::parse));
		as.requestObjectJWEEncs = extractFromJson(jsonObject, "request_object_encryption_enc_values_supported", extractNestedAdd(ArrayList::new, EncryptionMethod::parse));

		// Misc
		as.uiLocales = extractFromJson(jsonObject, "ui_locales_supported", extractNestedAdd(ArrayList::new, v -> {
			try {
				return LangTag.parse(v);
			} catch (LangTagException e) {
				throw new ParseException("Invalid ui_locales_supported field: " + e.getMessage(), e);
			}
		}));
		as.serviceDocsURI = extractFromJson(jsonObject, "service_documentation", readJsonUri);
		as.policyURI = extractFromJson(jsonObject, "op_policy_uri", readJsonUri);
		as.tosURI = extractFromJson(jsonObject, "op_tos_uri", readJsonUri);
		as.requestParamSupported = extractFromJson(jsonObject, "request_parameter_supported", readJsonBool, false);
		as.requestURIParamSupported = extractFromJson(jsonObject, "request_uri_parameter_supported", readJsonBool, true);
		as.requireRequestURIReg = extractFromJson(jsonObject, "require_request_uri_registration", readJsonBool, false);
		as.tlsClientCertificateBoundAccessTokens = extractFromJson(jsonObject, "tls_client_certificate_bound_access_tokens", readJsonBool, false);


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
		// TODO: add sender header with LogUtils.addSenderHeader
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
