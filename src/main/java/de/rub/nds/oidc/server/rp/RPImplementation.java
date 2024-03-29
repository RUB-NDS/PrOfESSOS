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

package de.rub.nds.oidc.server.rp;

import com.nimbusds.oauth2.sdk.ParseException;
import de.rub.nds.oidc.log.TestStepLogger;
import de.rub.nds.oidc.server.InvalidConfigurationException;
import de.rub.nds.oidc.server.OPIVConfig;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.server.TestNotApplicableException;
import de.rub.nds.oidc.test_model.ParameterType;
import de.rub.nds.oidc.test_model.RPConfigType;
import de.rub.nds.oidc.test_model.TestOPConfigType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

public interface RPImplementation {

	String REDIRECT_PATH = "/callback";
	String JWKS_PATH = "/jwks";


	void setRPConfig(RPConfigType cfg);

	void setOPIVConfig(OPIVConfig cfg);

	void setLogger(TestStepLogger logger);

	void setTestId(String testId);

	void setBaseUri(URI baseUri);

	void setRPType(RPType type);
	RPType getRPType();

	void setContext(Map<String, Object> suiteCtx, Map<String, Object> stepCtx);

	void setParameters(List<ParameterType> params);

	void setTestOPConfig(TestOPConfigType cfg);

	void runTestStepSetup() throws ParseException, IOException, InvalidConfigurationException, TestNotApplicableException;

	void prepareAuthnReq();

	// serve redirect_uri
	void callback(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException, URISyntaxException, ParseException;

	void jwks(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException;

	// TODO: add endpoints for request_uri, sector_identifier_uri, ?
//	void requestUri(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException;
//	void sectorIdentifierUri(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException;

}
