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
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Properties;
import javax.enterprise.context.ApplicationScoped;

/**
 *
 * @author Tobias Wich
 */
@ApplicationScoped
public class OPIVConfig {

	public OPIVConfig() throws IOException, URISyntaxException, GeneralSecurityException {
		InputStream hostsFile = OPIVConfig.class.getResourceAsStream("/servernames.properties");
		Properties p = new Properties();
		p.load(hostsFile);

		OP1_URL = new URI(p.getProperty("op1"));
		OP2_URL = new URI(p.getProperty("op2"));
		RP_URL = new URI(p.getProperty("rp"));

		InputStream keystoreFile = OPIVConfig.class.getResourceAsStream("/keystore.jks");
		keyStore = KeyStore.getInstance("JKS");
		keyStore.load(keystoreFile, keystorePass.toCharArray());
	}

	private final URI OP1_URL;
	private final URI OP2_URL;
	private final URI RP_URL;

	// TODO: read from config
	private final String signatureAlias = "opiv token signer";
	private final String keystorePass = "pass";
	private final KeyStore keyStore;

	public String getOP1Scheme() {
		return OP1_URL.getScheme();
	}

	public String getOP1Host() {
		String host = OP1_URL.getHost();
		int port = OP1_URL.getPort();
		return host + (port == -1 ? "" : ":" + port);
	}

	public String getOP2Scheme() {
		return OP2_URL.getScheme();
	}

	public String getOP2Host() {
		String host = OP2_URL.getHost();
		int port = OP2_URL.getPort();
		return host + (port == -1 ? "" : ":" + port);
	}

	public String getRPScheme() {
		return RP_URL.getScheme();
	}

	public String getRPHost() {
		String host = RP_URL.getHost();
		int port = RP_URL.getPort();
		return host + (port == -1 ? "" : ":" + port);
	}


	public KeyStore.PrivateKeyEntry getSigningEntry() throws GeneralSecurityException {
		KeyStore.ProtectionParameter pp = new KeyStore.PasswordProtection(keystorePass.toCharArray());
		KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(signatureAlias, pp);
		return entry;
	}

}
