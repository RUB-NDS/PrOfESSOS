package de.rub.nds.oidc.server.rp;

public class RPParameterConstants {

	// TestStep Parameter
	public static final String IS_SINGLE_RP_TEST = "is_single_rp_test";
	public static final String SCRIPT_EXEC_EXCEPTION_FAILS_TEST = "script_exception_fails_test";
	public static final String AUTH_ERROR_FAILS_TEST = "authentication-error-response-fails-test";
	public static final String SUCCESSFUL_CODE_REDEMPTION_FAILS_TEST = "code-redemption-fails-test";
	public static final String USER2_IN_USERINFO_FAILS_TEST = "username2-in-userinforesponse-fails-test";
	public static final String USER2_IN_IDTOKEN_SUB_FAILS_TEST = "username2-in-sub-claim-fails-test";

	// RP Config Parameter
	public static final String FORCE_CLIENT_REGISTRATION = "enforce-client-registration";

	public static final String REGISTER_GRANT_TYPE_IMPLICIT = "register-grant-type-implicit";
	public static final String REGISTER_GRANT_TYPE_AUTHCODE = "register-grant-type-authorization_code";
	public static final String REGISTER_GRANT_TYPE_REFRESH = "register-grant-type-refresh-token";

	public static final String REGISTER_RESPONSETYPE_CODE = "register-response-type-code";
	public static final String REGISTER_RESPONSETYPE_TOKEN = "register-response-type-token";
	public static final String REGISTER_RESPONSETYPE_IDTOKEN = "register-response-type-id_token";
	public static final String REGISTER_RESPONSETYPE_TOKEN_IDTOKEN = "register-response-type-token-id_token";
	public static final String REGISTER_RESPONSETYPE_CODE_IDTOKEN = "register-response-type-code-id_token";
	public static final String REGISTER_RESPONSETYPE_CODE_TOKEN = "register-response-type-code-token";
	public static final String REGISTER_RESPONSETYPE_CODE_TOKEN_IDTOKEN = "register-response-type-code-token-id_token";

	// BasicAuth is registered per default
	public static final String REGISTER_CLIENTAUTH_POST = "register-clientauth-method-client_secret_post";
	public static final String REGISTER_CLIENTAUTH_NONE = "register-clientauth-method-none";
	public static final String REGISTER_CLIENTAUTH_JWT = "register-clientauth-method-client_secret_jwt";
	public static final String REGISTER_CLIENTAUTH_PK_JWT = "register-clientauth-method-private_key_jwt";

	public static final String FORCE_EVIL_CLIENT_URI = "force_evil_client_uri";
	public static final String FORCE_EVIL_CLIENT_ID = "force_evil_client_id";
	public static final String FORCE_EVIL_CLIENT_SECRET = "force_evil_client_secret";
	public static final String FORCE_RANDOM_CLIENT_ID = "force_random_client_id";
	public static final String FORCE_RANDOM_CLIENT_SECRET = "force_random_client_secret";

	public static final String AUTHNREQ_FORCE_EVIL_REDIRURI = "authnreq-force-evil-redirect-uri";
	public static final String AUTHNREQ_ADD_SUBDOMAIN_REDIRURI = "authnreq-random-subdomain-in-redirect-uri";
	public static final String AUTHNREQ_ADD_PATHSUFFIX_REDIRURI = "authnreq-random-path-suffix-in-redirect-uri";
	public static final String AUTHNREQ_ADD_INVALID_TLD = "authnreq-invalid-top-level-domain-in-redirect-uri";

	public static final String AUTHNREQ_RU_PP_HONEST_FIRST = "authnreq-redirect_uri-parameter-pollution-honest-first";
	public static final String AUTHNREQ_RU_PP_EVIL_FIRST = "authnreq-redirect_uri-parameter-pollution-evil-first";
	public static final String AUTHNREQ_HONEST_USERPART_REDIRURI = "authnreq-honesturl-in-userpart-of-redirect-uri";
	public static final String USERINFOPART_EXCLUDE_SCHEME = "authnreq-userinfo-scheme-excluded";
	public static final String USERINFOPART_URL_ENCODE = "authnreq-userinfo-url-encode";

	public static final String AUTHNREQ_RESPONSE_MODE_QUERY = "authnreq-response_mode-query";
	public static final String AUTHNREQ_RESPONSE_MODE_FRAGMENT = "authnreq-response_mode-fragment";
	public static final String AUTHNREQ_RESPONSE_TYPE_FORM_POST = "authnreq-response_mode-formpost";

	public static final String AUTHNREQ_RESPONSE_TYPE_CODE = "authnreq-response_type-code"; // default
	public static final String AUTHNREQ_RESPONSE_TYPE_TOKEN = "authnreq-response_type-token";
	public static final String AUTHNREQ_RESPONSE_TYPE_IDTOKEN = "authnreq-response_type-id_token";

	public static final String AUTHNREQ_PKCE_METHOD_PLAIN = "authnreq-pkce-method-plain";
	public static final String AUTHNREQ_PKCE_METHOD_S_256 = "authnreq-pkce-method-s256";
	public static final String AUTHNREQ_PKCE_METHOD_EXCLUDED = "authnreq-pkce-exclude-method-param";
	public static final String AUTHNREQ_PKCE_CHALLENGE_EXCLUDED = "authnreq-pkce-exclude-challenge-param";

	public static final String AUTHNREQ_CLAIMSREQ_SUB1 = "authnreq-add-claimsreq-user1-sub";
	public static final String AUTHNREQ_CLAIMSREQ_ARRAY_SUB1 = "authnreq-add-claimsreq-array-user1-sub";
	public static final String AUTHNREQ_CLAIMSREQ_ARRAY_SUB1_SUB2 = "authnreq-add-claimsreq-array-sub1-sub2";
	public static final String AUTHNREQ_CLAIMSREQ_ARRAY_SUB2_SUB1 = "authnreq-add-claimsreq-array-sub2-sub1";
	public static final String AUTHNREQ_IDTOKEN_HINT_USER1 = "authnreq-add-id_token_hint-user1";

	public static final String TOKENREQ_FORCE_EVIL_REDIRURI = "tokenreq-force-evil-redirect-uri";
	public static final String TOKENREQ_REDIRURI_EXCLUDED = "tokenreq-exclude-redirect-uri";
	public static final String TOKENREQ_REDIRURI_ADD_SUBDOMAIN = "tokenreq-random-subdomain-in-redirect-uri";
	public static final String TOKENREQ_REDIRURI_ADD_PATHSUFFIX = "tokenreq-random-path-suffix-in-redirect-uri";
	public static final String TOKENREQ_REDIRURI_ADD_TLD = "tokenreq-add-invalid-tld-in-redirect-uri";

	public static final String TOKENREQ_FORCE_CLIENTAUTH_POST = "tokenreq-force-client-auth-post";
	public static final String TOKENREQ_CLIENTAUTH_EMPTY_ID = "tokenreq-clientauth-empty-client-id";
	public static final String TOKENREQ_CLIENTAUTH_EMPTY_SECRET = "tokenreq-clientauth-empty-client-secret";
	public static final String TOKENREQ_CLIENTSECRET_JWT_NONE_ALG = "tokenreq-client-jwt-none-alg";
	public static final String TOKENREQ_CLIENTSECRET_JWT_NONE_ALG_MIXEDCASE = "tokenreq-client-jwt-none-alg-mixedcase";
	public static final String TOKENREQ_CLIENTSECRET_JWT_INVALID_SIG = "tokenreq-client-jwt-invalid-sig";
	public static final String TOKENREQ_CLIENTSECRET_JWT_EXCLUDE_SIG = "tokenreq-client-jwt-exclude-sig";

	public static final String TOKENREQ_ADD_PKCE_METHOD_PLAIN = "tokenreq-add-pkce-method-param-plain";
	public static final String TOKENREQ_PKCE_INVALID = "tokenreq-invalid-pkce-verifier";
	public static final String TOKENREQ_PKCE_EXCLUDED = "tokenreq-pkce-verifier-excluded";
	public static final String TOKENREQ_PKCE_FROM_OTHER_SESSION = "tokenreq-pkce-from-other-session";

	public static final String FORCE_CODE_REUSE_USER = "force_auth-code_reuse_user";
	public static final String FORCE_NO_REDEEM_AUTH_CODE = "force-no-redeem-auth-code";
//	public static final String FORCE_USE_STORED_AUTH_CODE 	= "force-use-stored-auth-code";

}
