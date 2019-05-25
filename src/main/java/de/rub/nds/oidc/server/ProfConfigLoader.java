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
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import javax.enterprise.inject.Produces;


/**
 * Loader bean for the external config file.
 *
 * @author Tobias Wich
 */
public class ProfConfigLoader {

	private final ProfConfig cfg;

	public ProfConfigLoader() throws IOException, URISyntaxException, GeneralSecurityException {
		// load config from $HOME/professos.conf and merge with bundled reference.conf
		String homeDir = System.getProperty("user.home");
		File path = new File(homeDir, "professos.conf");
		// set property to load external file
		System.setProperty("config.url", path.toURI().toString());
		ConfigFactory.invalidateCaches();

		Config rawCfg = ConfigFactory.load();
		this.cfg = new ProfConfig(rawCfg);
	}

	@Produces
	ProfConfig getProfConfig() {
		return cfg;
	}

}
