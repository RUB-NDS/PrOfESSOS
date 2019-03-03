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

	public static final String FORCE_EVIL_CLIENT_URI = "force_evil_client_uri";
	public static final String FORCE_EVIL_CLIENT_ID = "force_evil_client_id";
	public static final String FORCE_EVIL_CLIENT_SECRET = "force_evil_client_secret";
	public static final String FORCE_RANDOM_CLIENT_ID = "force_random_client_id";
	public static final String FORCE_RANDOM_CLIENT_SECRET = "force_random_client_secret";

	public static final String AUTHNREQ_FORCE_EVIL_REDIRURI = "authnreq-force-evil-redirect-uri";
	public static final String AUTHNREQ_ADD_SUBDOMAIN_REDIRURI = "authnreq-random-subdomain-in-redirect-uri";
	public static final String AUTHNREQ_ADD_PATHSUFFIX_REDIRURI = "authnreq-random-path-suffix-in-redirect-uri";

	//	public static final String AUTHNREQ_RESPONSE_TYPE_CODE = "authnreq-response_type-code"; // default
	public static final String AUTHNREQ_RESPONSE_TYPE_TOKEN = "authnreq-response_type-token";
	public static final String AUTHNREQ_RESPONSE_TYPE_IDTOKEN = "authnreq-response_type-id_token";
	public static final String AUTHNREQ_RESPONSE_TYPE_TOKEN_IDTOKEN = "authnreq-response_type-token-id_token";
	public static final String AUTHNREQ_RESPONSE_TYPE_CODE_TOKEN_IDTOKEN = "authnreq-response_type-code-token-id_token";

	public static final String TOKENREQ_FORCE_EVIL_REDIRURI = "tokenreq-force-evil-redirect-uri";
	public static final String TOKENREQ_REDIRURI_EXCLUDED = "tokenreq-exclude-redirect-uri";
	public static final String TOKENREQ_REDIRURI_ADD_SUBDOMAIN = "tokenreq-random-subdomain-in-redirect-uri";
	public static final String TOKENREQ_REDIRURI_ADD_PATHSUFFIX = "tokenreq-random-path-suffix-in-redirect-uri";

	public static final String TOKENREQ_FORCE_CLIENTAUTH_POST = "tokenreq-force-client-auth-post";
	public static final String TOKENREQ_CLIENTAUTH_EMPTY_ID = "tokenreq-clientauth-empty-client-id";
	public static final String TOKENREQ_CLIENTAUTH_EMPTY_SECRET = "tokenreq-clientauth-empty-client-secret";


	public static final String FORCE_CODE_REUSE_USER = "force_auth-code_reuse_user";
	public static final String FORCE_NO_REDEEM_AUTH_CODE = "force-no-redeem-auth-code";
//	public static final String FORCE_USE_STORED_AUTH_CODE 	= "force-use-stored-auth-code";

}
