package de.rub.nds.oidc.server.rp;

public class RPParameterConstants {

	// TestStep Parameter
	public static final String IS_SINGLE_RP_TEST = "is_single_rp_test";

	// RP Config Parameter

    public static final String FORCE_REGISTER_CLIENT = "force-client-registration";

    public static final String REGISTER_GRANT_TYPES = "registration-grant-types";

	public static final String FORCE_HONEST_REDIRECT_URI 	= "force_honest_redirect_uri";
    public static final String FORCE_HONEST_CLIENT_URI 		= "force_honest_client_uri";
	public static final String FORCE_EMPTY_REDIRECT_URI = "tokenreq-force-empty-redirect-uri";
	public static final String FORCE_EVIL_REDIRECT_URI = "tokenreq-force-evil-redirect-uri";

	public static final String FORCE_CODE_REUSE_USER = "force_auth-code_reuse_user";
	public static final String FORCE_NO_REDEEM_AUTH_CODE = "force-no-redeem-auth-code";
//	public static final String FORCE_USE_STORED_AUTH_CODE = "force-use-stored-auth-code";

	public static final String FORCE_AUTHNREQ_EVIL_REDIRURI = "authnreq-force-evil-redirect-uri";


}
