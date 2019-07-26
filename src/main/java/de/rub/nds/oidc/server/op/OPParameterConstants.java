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

	public static final String BROWSER_INPUT_OP_URL        = "browser_input_op_url";
	public static final String BROWSER_INPUT_HONEST_OP_URL = "browser_input_honest-op_url";
	public static final String BROWSER_INPUT_EVIL_OP_URL   = "browser_input_evil-op_url";

	public static final String USE_EVIL_NEEDLE = "use_evil_needle";

	public static final String FORCE_SUCCESS_URL_FAILS = "force_success_url_fails";
	public static final String FORCE_USERINFO_REQUEST_FAILS = "force_userinfo_requested_fails";
	public static final String FORCE_TOKEN_REQUEST_FAILS = "force_token_requested_fails";

	public static final String FORCE_HONEST_DISCOVERY_ISS         = "force_honest_discovery_iss";
	public static final String FORCE_HONEST_DISCOVERY_REG_EP      = "force_honest_discovery_registrationEP";
	public static final String FORCE_HONEST_DISCOVERY_AUTH_EP     = "force_honest_discovery_authorizationEP";
	public static final String FORCE_HONEST_DISCOVERY_TOKEN_EP    = "force_honest_discovery_tokenEP";
	public static final String FORCE_HONEST_DISCOVERY_USERINFO_EP = "force_honest_discovery_userinfoEP";

	public static final String FORCE_HONEST_TOKEN_ISS      = "force_honest_idtoken_iss";
	public static final String FORCE_TOKEN_ISS_EXCL        = "force_idtoken_iss_excluded";
	public static final String FORCE_TOKEN_ISS_EMPTY       = "force_idtoken_iss_empty_string";
	public static final String FORCE_HONEST_TOKEN_SUB      = "force_honest_idtoken_sub";
	public static final String FORCE_HONEST_TOKEN_NAME     = "force_honest_idtoken_name";
	public static final String FORCE_HONEST_TOKEN_USERNAME = "force_honest_idtoken_username";
	public static final String FORCE_HONEST_TOKEN_EMAIL    = "force_honest_idtoken_email";
	public static final String FORCE_TOKEN_USERCLAIMS_EXCL = "force_token_userclaims_excluded";
	
	public static final String USERINFO_INCLUDE_HONEST_SUB    = "include_honest_sub_in_userinfo";
	public static final String USERINFO_INCLUDE_EVIL_SUB      = "include_evil_sub_in_userinfo";
	public static final String USERINFO_INCLUDE_HONEST_ISS    = "include_honest_iss_in_userinfo";
	public static final String USERINFO_INCLUDE_EVIL_ISS      = "include_evil_iss_in_userinfo";
	public static final String USERINFO_SUB_ARRAY             = "include_sub_claims_as_json_array";
	public static final String FORCE_HONEST_USERINFO_NAME     = "force_honest_userinfo_name";
	public static final String FORCE_HONEST_USERINFO_USERNAME = "force_honest_userinfo_username";
	public static final String FORCE_HONEST_USERINFO_EMAIL    = "force_honest_userinfo_email";
	
	public static final String FORCE_TOKENHEADER_CLAIMS       = "token_header_add_claims";
	public static final String FORCE_TOKENHEADER_HONEST_SUB   = "force_token_header_honest_sub";
	public static final String FORCE_TOKENHEADER_HONEST_ISS   = "force_token_header_honest_iss";
	public static final String FORCE_TOKENHEADER_HONEST_EMAIL = "force_token_header_honest_email";
	

	public static final String FORCE_TOKEN_EXP_DAY       = "force_idtoken_exp_oneday";
	public static final String FORCE_TOKEN_EXP_YEAR      = "force_idtoken_exp_oneyear";
	public static final String FORCE_TOKEN_IAT_DAY       = "force_idtoken_iat_oneday";
	public static final String FORCE_TOKEN_IAT_YEAR      = "force_idtoken_iat_oneyear";
	public static final String FORCE_TOKEN_NONCE_INVALID = "force_idtoken_nonce_invalidValue";
	public static final String FORCE_TOKEN_NONCE_EXCL    = "force_idtoken_nonce_excluded";
	public static final String FORCE_TOKEN_SIG_INVALID   = "force_idtoken_signature_invalidValue";
	public static final String FORCE_TOKEN_SIG_NONE      = "force_idtoken_header_alg_none";
	public static final String FORCE_TOKEN_SIG_NONE_MIXEDCASE = "force_idtoken_header_alg_none2";

	public static final String FORCE_TOKEN_AT_HASH_INVALID = "force_idtoken_at_hash_invalid";
	public static final String FORCE_TOKEN_CODE_HASH_INVALID = "force_idtoken_code_hash_invalid";
	
	public static final String FORCE_TOKEN_AUD_EXCL      = "force_idtoken_aud_excluded";
	public static final String FORCE_TOKEN_AUD_INVALID   = "force_idtoken_aud_invalidValue";
	public static final String FORCE_TOKEN_HONEST_AUD    = "force_idtoken_honest_aud";

	public static final String FORCE_STATE_INVALID_VALUE = "force_state_invalidValue";
	public static final String FORCE_STATE_OTHER_SESSION = "force_state_fromotherSession";
	public static final String FORCE_STATE_EXCL          = "force_state_excluded";

	public static final String INCLUDE_SIGNING_CERT      = "include_signing_certificate";

	// this will only be effective if discovery/registration is enforced using
	public static final String OP_MD_JWSA_HS256 = "op_configuration_include_jws_algorithm_hs256";
	public static final String OP_MD_JWSA_RS256 = "op_configuration_include_jws_algorithm_rs256";
	public static final String OP_MD_JWSA_NONE = "op_configuration_include_jws_algorithm_none";

	// KeyConfusion
	public static final String IDTOKEN_SPOOFED_JWK = "idtoken_spoofed_jwk";
	public static final String IDTOKEN_SPOOFED_JKU = "idtoken_spoofed_jku";
	public static final String IDTOKEN_SPOOFED_X5U = "idtoken_spoofed_x5u";
	public static final String IDTOKEN_SPOOFED_X5C = "idtoken_spoofed_x5c";

	public static final String JKU_TRUSTED_FIRST = "jku_return_trusted-untrusted_jwks"; // jwks returns [trusted, untrusted]
	public static final String JKU_UNTRUSTED_FIRST = "jku_return_untrusted-trusted_jwks"; // skip if jku not requested in jku-1 test

	public static final String X5C_TRUSTED_FIRST = "idtoken_x5c_trusted-untrusted";
	public static final String X5C_UNTRUSTED_FIRST = "idtoken_x5c_untrusted-trusted";

	public static final String FORCE_NEW_KID_IN_IDTOKEN_AND_JWK = "include_fresh_keyid_in_jwt_and_jwk";

	// very experimental
	public static final String IDTOKEN_SPOOFED_JKU_AS_KID = "force_spoofed_jku_as_kid";
	public static final String IDTOKEN_SPOOFED_JWK_AS_KID = "force_spoofed_jwk_as_kid";
	public static final String IDTOKEN_SPOOFED_JKU_AS_JWK = "idtoken_spoofed_jku_as_jwk";
	public static final String IDTOKEN_SPOOFED_JKU_IN_JWK = "idtoken_spoofed_jku_in_jwk";
	public static final String IDTOKEN_SPOOFED_X5U_IN_JWK = "idtoken_spoofed_x5u_in_jwk";
	public static final String IDTOKEN_SPOOFED_X5C_IN_JWK = "idtoken_spoofed_x5c_in_jwk";
	// */* //

	public static final String IDTOKEN_SPOOFED_SECRET_KEY_= "idtoken_spoofed_secret_key_in_jwk";
	public static final String IDTOKEN_HMAC_PUBKEY_e = "hmac_pubkey_e";
	public static final String IDTOKEN_HMAC_PUBKKEY_n = "hmac_pubkey_n";
	public static final String IDTOKEN_HMAC_PUBKEY_kty = "hmac_pubkey_kty";
	public static final String IDTOKEN_HMAC_PUBKEY_alg = "hmac_pubkey_alg";
	public static final String IDTOKEN_HMAC_PUBKEY_JWKSTRING = "hmac_jsonstring_jwk";
	public static final String LEFTPAD_SHORT_HMAC_KEYS = "zeropad_short_hmac_keys_to_32byte"; // TODO: no test step yet

	public static final String IDTOKEN_HMAC_PUBKEY_PKCS1 = "hmac_pkcs1_pubkey";
	public static final String P1_KEY_CONFUSION_PAYLOAD_TYPE = "pkcs1_keyconfusion_payload_type";
	public static final String IDTOKEN_HMAC_PUBKEY_PKCS8 = "hmac_pkcs8_pubkey";
	public static final String P8_KEY_CONFUSION_PAYLOAD_TYPE = "pkcs8_keyconfusion_payload_type";

	public static final String IDTOKEN_CRITICAL_JKU = "idtoken_crit_jku";
	public static final String IDTOKEN_CRITICAL_X5C = "idtoken_crit_x5c";
	public static final String IDTOKEN_CRITICAL_X5U = "idtoken_crit_x5u";
	public static final String IDTOKEN_CRITICAL_JWK = "idtoken_crit_jwk";
	public static final String IDTOKEN_CRITICAL_KID = "idtoken_crit_kid";

	public static final String FORCE_UNTRUSTED_KEY_REQUEST_FAILS = "request_to_untrusted_key_uri_fails_test";

	// KC6 with Session Overwriting
	public static final String FORCE_IDTOKEN_SIGNING_ALG_HS256 = "force_idtoken_signing_alg_hs256";
	public static final String FORCE_REGISTER_HONEST_CLIENTID = "force_register_honest_client_id";
	
}
