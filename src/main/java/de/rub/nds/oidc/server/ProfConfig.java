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

import com.typesafe.config.Config;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;


/**
 *
 * @author Tobias Wich
 */
public class ProfConfig {

	private final Config profCfg;
	private final OPIVConfig endpointCfg;
	private final Config seleniumCfg;

	public ProfConfig(Config rawCfg) throws IOException, URISyntaxException, GeneralSecurityException {
		this.profCfg = rawCfg.getConfig("professos");
		this.endpointCfg = new OPIVConfig(profCfg);
		this.seleniumCfg = profCfg.getConfig("selenium");
	}

	public Config getSeleniumCfg() {
		return seleniumCfg;
	}

	public OPIVConfig getEndpointCfg() {
		return endpointCfg;
	}

	public int getSessionLifetime() {
		return profCfg.getInt("session-lifetime");
	}

	public int getTestIdLength() {
		return profCfg.getInt("test-id-length");
	}

}
