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
import javax.ws.rs.core.UriBuilder;

/**
 *
 * @author Tobias Wich
 */
@ApplicationScoped
public class OPIVConfig {
	private final URI CONTROLLER_URI;
	private final URI HONEST_OP_URL;
	private final URI EVIL_OP_URL;
	private final URI HONEST_RP_URL;
	private final URI EVIL_RP_URL;

	// TODO: read from config
	private final String honestSigAlias = "opiv honest token signer";
	private final String evilSigAlias = "opiv evil token signer";
	private final String keystorePass = "pass";
	private final KeyStore keyStore;

	public OPIVConfig() throws IOException, URISyntaxException, GeneralSecurityException {
		InputStream hostsFile = OPIVConfig.class.getResourceAsStream("/servernames.properties");
		Properties p = new Properties();
		p.load(hostsFile);

		CONTROLLER_URI = new URI(p.getProperty("controller"));
		HONEST_OP_URL = new URI(p.getProperty("honest-op"));
		EVIL_OP_URL = new URI(p.getProperty("evil-op"));
		HONEST_RP_URL = new URI(p.getProperty("honest-rp"));
		EVIL_RP_URL = new URI(p.getProperty("evil-rp"));

		InputStream keystoreFile = OPIVConfig.class.getResourceAsStream("/keystore.jks");
		keyStore = KeyStore.getInstance("JKS");
		keyStore.load(keystoreFile, keystorePass.toCharArray());
	}


	public URI getControllerUri() {
		return CONTROLLER_URI;
	}

	public URI getHonestOPUri() {
		return UriBuilder.fromUri(HONEST_OP_URL).path("/dispatch/").build();
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
		return UriBuilder.fromUri(EVIL_OP_URL).path("/dispatch/").build();
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
		return UriBuilder.fromUri(HONEST_RP_URL).path("/dispatch/").build();
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
		return UriBuilder.fromUri(EVIL_RP_URL).path("/dispatch/").build();
	}
	public String getEvilRPScheme() {
		return EVIL_RP_URL.getScheme();
	}

	public String getEvilRPHost() {
		String host = EVIL_RP_URL.getHost();
		int port = EVIL_OP_URL.getPort();
		return host + (port == -1 ? "" : ":" + port);
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
