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

/**
 *
 * @author Tobias Wich
 */
public class OPContextConstants {

	public static final String REGISTRATION_NEEDED = "dynamic_client_registration_support_needed";

	private static final String PFX = "op.";

	public static final String REGISTERED_CLIENT_INFO_HONEST = PFX + "registered-client-info-honest";
	public static final String REGISTERED_CLIENT_INFO_EVIL = PFX + "registered-client-info-evil";

	public static final String AUTH_REQ_NONCE = PFX + "auth-reg-nonce";

	public static final String TOKEN_INFORMATIONLEAK_FUTURE = PFX + "token-information-leak-future";
	public static final String USERINFO_INFORMATIONLEAK_FUTURE = PFX + "userinfo-information-leak-future";

	public static final String HONEST_CODE = PFX + "honest-code";
	public static final String HONEST_ACCESSTOKEN = PFX + "honest-accesstoken";

	public static final String BLOCK_OP_FUTURE = PFX + "block-op-future";
	public static final String BLOCK_BROWSER_FUTURE = PFX + "block-browser-future";
	public static final String RELOAD_BROWSER_FUTURE = PFX + "reload-browser-future";

//	public static final String BLOCK_HONEST_OP_FUTURE = PFX + "block-honest-op-future";
	public static final String BLOCK_EVIL_OP_FUTURE = PFX + "block-evil-op-future";
	
	// confusion
	public static final String BLOCK_BROWSER_WAITING_FOR_HONEST = PFX + "block-browser-waiting-for-honest";
	public static final String BLOCK_BROWSER_AND_TEST_RESULT = PFX + "block-browser-and-test-result";
	public static final String AUTH_REQ_HONEST_NONCE = PFX + "auth-reg-honest-nonce";
	public static final String AUTH_REQ_HONEST_STATE = PFX + "auth-reg-honest-state";

	// key confusion
	public static final String UNTRUSTED_KEY_REQUESTED = PFX + "untrusted-key-request-received";
	public static final String MULTI_PART_TEST_FINISHED = PFX + "all-test-runs-finished";
}
