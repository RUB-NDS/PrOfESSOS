/****************************************************************************
 * Copyright 2016-2019 Ruhr-Universität Bochum.
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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Optional;
import javax.ws.rs.core.UriBuilder;


/**
 *
 * @author Tobias Wich
 */
public class OPIVConfig {

	private final URI CONTROLLER_URI;
	private final URI HONEST_OP_URL;
	private final URI EVIL_OP_URL;
	private final URI HONEST_RP_URL;
	private final URI EVIL_RP_URL;

	private final Optional<URI> TEST_RP_URL;
	private final Optional<URI> TEST_OP_URL;

	// TODO: read from config
	private String honestSigAlias = "opiv honest token signer";
	private String evilSigAlias = "opiv evil token signer";
	private String untrustedAlias = "opiv untrusted token signer";
	private String keystorePass = "pass";
	private final KeyStore keyStore;

	private final boolean allowCustomTestIDs;
	private final boolean allowTestWithoutRemotePermission;
	private final boolean disableTlsTrustCheck;

	public OPIVConfig(Config profCfg) throws IOException, URISyntaxException, GeneralSecurityException {
		var endpointCfg = profCfg.getConfig("endpoints");

		CONTROLLER_URI = new URI(endpointCfg.getString("controller"));
		HONEST_OP_URL = new URI(endpointCfg.getString("honest-op"));
		EVIL_OP_URL = new URI(endpointCfg.getString("evil-op"));
		HONEST_RP_URL = new URI(endpointCfg.getString("honest-rp"));
		EVIL_RP_URL = new URI(endpointCfg.getString("evil-rp"));

		if (endpointCfg.hasPath("test-rp")) {
			TEST_RP_URL = Optional.of(new URI(endpointCfg.getString("test-rp")));
		} else {
			TEST_RP_URL = Optional.empty();
		}
		if (endpointCfg.hasPath("test-op")) {
			TEST_OP_URL = Optional.of(new URI(endpointCfg.getString("test-op")));
		} else {
			TEST_OP_URL = Optional.empty();
		}

		InputStream ksStream = null;
		// TODO: load keystore from file if specified
		if (endpointCfg.hasPath("keystore-file")) {
			String ksFileStr = endpointCfg.getString("keystore-file");
			// correct path if it is absolute or relative to config file
			File ksFile = new File(ksFileStr);
			if (! ksFile.isAbsolute()) {
				String basePath = endpointCfg.getValue("keystore-file").origin().filename();
				ksFile = new File(basePath, ksFileStr);
			}

			// only load if file is readable
			if (ksFile.isFile() && ksFile.canRead()) {
				ksStream = new FileInputStream(ksFile);
				// load alias and so on
				honestSigAlias = endpointCfg.getString("honest-sig-alias");
				evilSigAlias = endpointCfg.getString("evil-sig-alias");
				untrustedAlias = endpointCfg.getString("untrusted-alias");
				keystorePass = endpointCfg.getString("keystore-pass");
			}
		}

		// fallback to integrated keystore
		if (ksStream == null) {
			ksStream = OPIVConfig.class.getResourceAsStream("/keystore.jks");
		}

		this.keyStore = KeyStore.getInstance("JKS");
		this.keyStore.load(ksStream, keystorePass.toCharArray());

		// use config file to set these parameters
		allowCustomTestIDs = profCfg.getBoolean("allow-custom-test-ids");
		allowTestWithoutRemotePermission = profCfg.getBoolean("skip-target-grant");
		disableTlsTrustCheck = profCfg.getBoolean("disable-tls-trust-check");
	}


	public URI getControllerUri() {
		return CONTROLLER_URI;
	}

	public URI getHonestOPUri() {
		return UriBuilder.fromUri(HONEST_OP_URL).path("/").build();
	}

	public String getHonestOPScheme() {
		return HONEST_OP_URL.getScheme();
	}

	public String getHonestOPHost() {
		String host = HONEST_OP_URL.getHost();
		int port = HONEST_OP_URL.getPort();
		return host + (port == -1 ? "" : ":" + port);
	}

	public URI getEvilOPUri() {
		return UriBuilder.fromUri(EVIL_OP_URL).path("/").build();
	}

	public String getEvilOPScheme() {
		return EVIL_OP_URL.getScheme();
	}

	public String getEvilOPHost() {
		String host = EVIL_OP_URL.getHost();
		int port = EVIL_OP_URL.getPort();
		return host + (port == -1 ? "" : ":" + port);
	}

	public URI getHonestRPUri() {
		return UriBuilder.fromUri(HONEST_RP_URL).path("/").build();
	}

	public String getHonestRPScheme() {
		return HONEST_RP_URL.getScheme();
	}

	public String getHonestRPHost() {
		String host = HONEST_RP_URL.getHost();
		int port = HONEST_RP_URL.getPort();
		return host + (port == -1 ? "" : ":" + port);
	}

	public URI getEvilRPUri() {
		return UriBuilder.fromUri(EVIL_RP_URL).path("/").build();
	}
	public String getEvilRPScheme() {
		return EVIL_RP_URL.getScheme();
	}

	public String getEvilRPHost() {
		String host = EVIL_RP_URL.getHost();
		int port = EVIL_OP_URL.getPort();
		return host + (port == -1 ? "" : ":" + port);
	}

	public Optional<URI> getTestOPUri() {
		return TEST_OP_URL;
	}

	public Optional<URI> getTestRPUri() {
		return TEST_RP_URL;
	}

	public boolean isAllowCustomTestIDs() {
		return allowCustomTestIDs;
	} 

	public boolean isGrantNotNeededOverride() {
		return allowTestWithoutRemotePermission;
	}

	public boolean isDisableTlsTrustCheck() {
		return disableTlsTrustCheck;
	}

	public KeyStore.PrivateKeyEntry getHonestOPSigningEntry() {	return getSigningEntry(honestSigAlias);	}

	public KeyStore.PrivateKeyEntry getEvilOPSigningEntry() { return getSigningEntry(evilSigAlias);	}

	public KeyStore.PrivateKeyEntry getUntrustedSigningEntry() { return getSigningEntry(untrustedAlias); }

	public KeyStore.PrivateKeyEntry getSigningEntry(String entryAlias) {
		try {
			KeyStore.ProtectionParameter pp = new KeyStore.PasswordProtection(keystorePass.toCharArray());
			KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(entryAlias, pp);
			return entry;
		} catch (GeneralSecurityException ex) {
			throw new IllegalArgumentException("Failed to access keystore.", ex);
		}
	}

}
