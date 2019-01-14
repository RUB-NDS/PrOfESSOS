package de.rub.nds.oidc.server.rp;

public class RPContextConstants {

    private static final String PFX = "rp.";

	public static final String IS_SINGLE_RP_TEST = "is_single_rp_test";

	public static final String STEP_SETUP_FINISHED = PFX + "test-setup-result";
	public static final String RP_INDICATED_STEP_RESULT = PFX + "rp-indicated-test-result";

	public static final String RP1_PREPARED_AUTHNREQ = PFX + "prepared-rp1-authnreq";
	public static final String RP2_PREPARED_AUTHNREQ = PFX + "prepared-rp2-authnreq";

	public static final String OP_INFO_HONEST_CLIENT = PFX + "op-info-honest-client";
	public static final String OP_INFO_EVIL_CLIENT = PFX + "op-info-evil-client";

	public static final String TARGET_OP_URL = PFX + "op_target_url";
	public static final String DISCOVERED_OP_CONFIGURATION = PFX + "discovered-op-config";

	// these are available in the testconfig, not needed here
//	public static final String USER1_USERNAME = "user1_username";
//	public static final String USER1_PASSWORD = "user1_password";
//	public static final String USER2_USERNAME = "user2_username";
//	public static final String USER2_PASSWORD = "user2_password";

	public static final String CURRENT_USER_USERNAME = "current_user_username";
	public static final String CURRENT_USER_PASSWORD = "current_user_password";

	public static final String HONEST_CLIENT_CLIENTINFO = PFX + "1-registered-client-info";
	public static final String EVIL_CLIENT_CLIENTINFO = PFX + "2-registered-client-info";

	public static final String CLIENT_REGISTRATION_FAILED = PFX + "client-not-registered";

	public static final String BLOCK_BROWSER_FOR_RP_FUTURE = PFX + "block-browser-for-rp-future";
	public static final String BLOCK_RP_FOR_BROWSER_FUTURE = PFX + "block-browser-for-rp-future";
	public static final String BLOCK_BROWSER_AND_TEST_RESULT = PFX + "block-browser-for-rp-testresult-future";

	public static final String LAST_BROWSER_URL = PFX + "last-url-seeen-in-browser";

}
