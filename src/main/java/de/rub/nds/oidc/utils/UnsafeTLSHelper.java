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

package de.rub.nds.oidc.utils;

import de.rub.nds.oidc.server.OPIVConfig;
import javax.annotation.Nullable;
import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;


public class UnsafeTLSHelper {

	private final boolean trustAll;
	private SSLContext sslContext;
	
	public UnsafeTLSHelper(OPIVConfig cfg) {
		this.trustAll = cfg.isDisableTlsTrustCheck();
		initialiseContext();
	} 
	
	private void initialiseContext() {
		try {
			TrustManager[] trustAllCerts = new TrustManager[]{
					new X509TrustManager() {
						@Override
						public X509Certificate[] getAcceptedIssuers() {
							return null;
						}

						@Override
						public void checkClientTrusted(X509Certificate[] certs, String authType) {
						}

						@Override
						public void checkServerTrusted(X509Certificate[] certs, String authType) {
						}
					}
			};

			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			this.sslContext = sc;
		} catch (NoSuchAlgorithmException | KeyManagementException e) {
			// TODO
		}
	}

	@Nullable
	public SSLSocketFactory getTrustAllSocketFactory() {
		if (! trustAll) {
			return null;
		}
		return sslContext.getSocketFactory();
	}


	@Nullable
	public HostnameVerifier getTrustAllHostnameVerifier() {
		if (! trustAll) {
			return null;
		}
		HostnameVerifier nopVerifier = (String hostname, SSLSession session) -> true; 
		return nopVerifier;
	}

}
