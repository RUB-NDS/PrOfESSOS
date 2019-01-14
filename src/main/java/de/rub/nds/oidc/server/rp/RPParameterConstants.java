package de.rub.nds.oidc.server.rp;

public class RPParameterConstants {

	// TestStep Parameter
	public static final String IS_SINGLE_RP_TEST = "is_single_rp_test";

	// RP Config Parameter
	private static final String PFX = "rp.";

    public static final String FORCE_REGISTER_CLIENT = PFX + "force-client-registration";

    public static final String REGISTER_GRANT_TYPES = PFX + "registration-grant-types";

	public static final String FORCE_HONEST_REDIRECT_URI 	= PFX + "force_honest_redirect_uri";
    public static final String FORCE_HONEST_CLIENT_URI 		= PFX + "force_honest_client_uri";

	public static final String FORCE_CODE_REUSE_USER = PFX  + "force_auth-code_reuse_user";
	public static final String FORCE_UNUSED_HONEST_AUTH_CODE = PFX  + "unused_auth-code_from_honest_in_evil";


}
