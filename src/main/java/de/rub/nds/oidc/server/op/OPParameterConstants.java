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
public class OPParameterConstants {

	public static final String BROWSER_INPUT_OP_URL = "browser.input.op_url";
	public static final String BROWSER_INPUT_HONEST_OP_URL = "browser.input.honest_op_url";
	public static final String BROWSER_INPUT_EVIL_OP_URL = "browser.input.evil_op_url";

	public static final String USE_EVIL_NEEDLE = "use_evil_needle";

	public static final String FORCE_HONEST_DISCOVERY_ISS         = "force_honest_discovery_iss";
	public static final String FORCE_HONEST_DISCOVERY_REG_EP      = "force_honest_discovery_registrationEP";
	public static final String FORCE_HONEST_DISCOVERY_AUTH_EP     = "force_honest_discovery_authorizationEP";
	public static final String FORCE_HONEST_DISCOVERY_TOKEN_EP    = "force_honest_discovery_tokenEP";
	public static final String FORCE_HONEST_DISCOVERY_USERINFO_EP = "force_honest_discovery_userinfoEP";

	public static final String FORCE_HONEST_TOKEN_ISS      = "force_honest_idtoken_iss";
	public static final String FORCE_HONEST_TOKEN_SUB      = "force_honest_idtoken_sub";
	public static final String FORCE_HONEST_TOKEN_NAME     = "force_honest_idtoken_name";
	public static final String FORCE_HONEST_TOKEN_USERNAME = "force_honest_idtoken_username";
	public static final String FORCE_HONEST_TOKEN_EMAIL    = "force_honest_idtoken_email";

	public static final String FORCE_TOKEN_EXP_DAY       = "force_idtoken_exp_oneday";
	public static final String FORCE_TOKEN_EXP_YEAR      = "force_idtoken_exp_oneyear";
	public static final String FORCE_TOKEN_IAT_DAY       = "force_idtoken_iat_oneday";
	public static final String FORCE_TOKEN_IAT_YEAR      = "force_idtoken_iat_oneyear";
	public static final String FORCE_TOKEN_NONCE_INVALID = "force_idtoken_nonce_invalidValue";
	public static final String FORCE_TOKEN_NONCE_EXCL    = "force_idtoken_nonce_excluded";
	public static final String FORCE_TOKEN_SIG_INVALID   = "force_idtoken_signature_invalidValue";
	public static final String FORCE_TOKEN_SIG_NONE      = "force_idtoken_header_alg_none";
	public static final String FORCE_TOKEN_AUD_EXCL      = "force_idtoken_aud_excluded";
	public static final String FORCE_TOKEN_AUD_INVALID   = "force_idtoken_aud_invalidValue";
	public static final String FORCE_TOKEN_HONEST_AUD    = "force_idtoken_honest_aud";

	public static final String FORCE_STATE_INVALID_VALUE = "force_state_invalidValue";
	public static final String FORCE_STATE_OTHER_SESSION = "force_state_fromotherSession";

}
