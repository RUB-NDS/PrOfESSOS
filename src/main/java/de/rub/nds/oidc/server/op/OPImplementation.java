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

import de.rub.nds.oidc.log.TestStepLogger;
import de.rub.nds.oidc.server.OPIVConfig;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.test_model.OPConfigType;
import de.rub.nds.oidc.test_model.ParameterType;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 *
 * @author Tobias Wich
 */
public interface OPImplementation {

	public static final String WEBFINGER_PATH = "/.well-known/webfinger";
	public static final String PROVIDER_CONFIG_PATH = "/.well-known/openid-configuration";
	public static final String JWKS_PATH = "/jwks";
	public static final String REGISTER_CLIENT_PATH = "/register";
	public static final String AUTH_REQUEST_PATH = "/auth-req";
	public static final String TOKEN_REQUEST_PATH = "/token-req";
	public static final String USER_INFO_REQUEST_PATH = "/user-info";
	// key material for key confusion attacks
	public static final String UNTRUSTED_KEY_PATH = "/untrusted-key";


	void setOPConfig(OPConfigType cfg);

	void setOPIVConfig(OPIVConfig cfg);

	void setLogger(TestStepLogger logger);

	void setTestId(String testId);

	void setBaseUri(URI baseUri);

	void setOPType(OPType type);
	OPType getOPType();

	void setContext(Map<String, Object> suiteCtx, Map<String, Object> stepCtx);

	void setParameters(List<ParameterType> params);


	void webfinger(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException;

	void providerConfiguration(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException;

	void jwks(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException;

	void registerClient(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException;

	void authRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException;

	void tokenRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException;

	void userInfoRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException;

	void untrustedKeyRequest(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException;

}
