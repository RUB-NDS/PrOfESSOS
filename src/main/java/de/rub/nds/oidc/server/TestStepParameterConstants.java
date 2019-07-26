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

package de.rub.nds.oidc.server;


public class TestStepParameterConstants {

	public static final String RESPONSE_TYPE_CONDITION_CODE = "step-condition-response-type-contains-code";
	public static final String RESPONSE_TYPE_CONDITION_IDTOKEN = "step-condition-response-type-contains-idtoken";
	public static final String RESPONSE_TYPE_CONDITION_TOKEN = "step-condition-response-type-contains-token";

	public static final String SCOPE_CONDITION_OPENID_NOT_NEEDED = "step-condition-scope-openid-not-needed";

	public static final String DISCOVERY_REQUEST_REQUIRED = "discovery_support_needed";

	public static final String CLIENT_PROFILE_NAME = "client-profile-name";
	
	public static final String CLIENT_PROFILE_CLIENT_SECRET_POST = "profile-client-auth-post";
	public static final String CLIENT_PROFILE_CLIENT_SECRET_JWT = "profile-client-auth-client_secret_jwt";
	public static final String CLIENT_PROFILE_IMPLICIT = "profile-client-implicit-flow";
	public static final String CLIENT_PROFILE_HYBRID = "profile-client-hybrid-flow";
	
}
