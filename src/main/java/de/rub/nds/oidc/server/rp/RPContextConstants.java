/****************************************************************************
 * Copyright 2019 Ruhr-Universität Bochum.
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

package de.rub.nds.oidc.server.rp;


public class RPContextConstants {

	public static final String CURRENT_USER_USERNAME = "current_user_username";
	public static final String CURRENT_USER_PASSWORD = "current_user_password";
	public static final String START_RP_TYPE = "start-rp-type";  // "EVIL" or "HONEST" 
	private static final String PFX = "rp.";

	public static final String IS_RP_LEARNING_STEP = PFX + "is_learning_teststep";
	public static final String STEP_SETUP_FINISHED = PFX + "test-setup-result";
	public static final String RP1_PREPARED_AUTHNREQ = PFX + "prepared-rp1-authnreq";
	public static final String RP2_PREPARED_AUTHNREQ = PFX + "prepared-rp2-authnreq";
	public static final String RP1_PREPARED_REDIRECT_URI = PFX + "rp1-redirect-uri";
	public static final String RP2_PREPARED_REDIRECT_URI = PFX + "rp2-redirect-uri";
	public static final String RP1_AUTHNREQ_RT = PFX + "rp1-authnreq-response-type";
	public static final String RP2_AUTHNREQ_RT = PFX + "rp2-authnreq-response-type";

	public static final String OP_INFO_HONEST_CLIENT = PFX + "op-info-honest-client";
	public static final String OP_INFO_EVIL_CLIENT = PFX + "op-info-evil-client";

	public static final String TARGET_OP_URL = PFX + "op_target_url";
	public static final String DISCOVERED_OP_CONFIGURATION = PFX + "discovered-op-config";
	public static final String HONEST_CLIENT_CLIENTINFO = PFX + "1-registered-client-info";
	public static final String EVIL_CLIENT_CLIENTINFO = PFX + "2-registered-client-info";

	public static final String BLOCK_BROWSER_FOR_RP_FUTURE = PFX + "block-browser-for-rp-future";
	public static final String BLOCK_RP_FOR_BROWSER_FUTURE = PFX + "block-browser-for-rp-future";
	public static final String BLOCK_BROWSER_AND_TEST_RESULT = PFX + "block-browser-for-rp-testresult-future";

	public static final String LAST_BROWSER_URL = PFX + "last-url-seeen-in-browser";

	public static final String STORED_AUTH_CODE = PFX + "stored-auth-code";
	public static final String STORED_PKCE_VERIFIER = PFX + "stored-pkce-verifier";
	public static final String STORED_USER1_IDTOKEN = PFX + "stored-idtoken-user1";
	public static final String STORED_USER2_IDTOKEN = PFX + "stored-idtoken-user2";
	public static final String STORED_USER1_SUB_VAL = PFX + "stored-user1-sub-val";

	public static final String MANIPULATED_REDIRECT_URI = PFX + "stored-manipulated-redirect-uri";
	public static final String REDIRECT_URI_MANIPULATOR = PFX + "stored-uri-manipulator-string";
	public static final String INVALID_TLD = "invalid";

}
