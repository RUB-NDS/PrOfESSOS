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

package de.rub.nds.oidc.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import javax.enterprise.context.ApplicationScoped;

/**
 *
 * @author Tobias Wich
 */
@ApplicationScoped
public class EndpointHosts {

	public EndpointHosts() throws IOException {
		InputStream hostsFile = EndpointHosts.class.getResourceAsStream("/servernames.properties");
		Properties p = new Properties();
		p.load(hostsFile);

		OP1_HOST = p.getProperty("op1");
		OP2_HOST = p.getProperty("op2");
		RP_HOST = p.getProperty("rp");
	}

	private final String OP1_HOST;
	private final String OP2_HOST;
	private final String RP_HOST;

	public String getOP1Host() {
		return OP1_HOST;
	}

	public String getOP2Host() {
		return OP2_HOST;
	}

	public String getRPHost() {
		return RP_HOST;
	}

}
