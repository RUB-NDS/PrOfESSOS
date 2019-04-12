package de.rub.nds.oidc.utils;

import javax.annotation.Nullable;
import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

public class UnsafeTLSHelper {

	private static final boolean trustAll = Boolean.parseBoolean(System.getenv("TRUST_ALL_TLS"));


	@Nullable
	public static SSLSocketFactory getTrustAllSocketFactory() {
		if (!trustAll) {
			return null;
		}
		try {
			TrustManager[] trustAllCerts = new TrustManager[]{
					new X509TrustManager() {
						public X509Certificate[] getAcceptedIssuers() {
							return null;
						}

						public void checkClientTrusted(X509Certificate[] certs, String authType) {
						}

						public void checkServerTrusted(X509Certificate[] certs, String authType) {
						}
					}
			};

			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			return sc.getSocketFactory();

		} catch (NoSuchAlgorithmException e) {
			// TODO
		} catch (KeyManagementException e) {
			// TODO
		}

		return null;
	}

	@Nullable
	public static HostnameVerifier getTrustAllHostnameVerifier() {
		if (!trustAll) {
			return null;
		}
		HostnameVerifier hv = new HostnameVerifier() {
			public boolean verify(String hostname, SSLSession session) {
				return true;
			}
		};
		return hv;
	}

}
