package de.rub.nds.oidc.utils;

import javax.annotation.Nullable;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

public class UnsafeTLSHelper {
	private final boolean trustAll;
	private SSLContext sslContext;
	
	public UnsafeTLSHelper() {
		this.trustAll = Boolean.parseBoolean(System.getenv("TRUST_ALL_TLS"));
		initialiseContext();
	} 
	
	private void initialiseContext() {
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
			this.sslContext = sc;
		} catch (NoSuchAlgorithmException | KeyManagementException e) {
			// TODO
		}
	}

	@Nullable
	public SSLSocketFactory getTrustAllSocketFactory() {
		if (!trustAll) {
			return null;
		}
		return sslContext.getSocketFactory();
	}


	@Nullable
	public HostnameVerifier getTrustAllHostnameVerifier() {
		if (!trustAll) {
			return null;
		}
		HostnameVerifier nopVerifier = (String hostname, SSLSession session) -> true; 
		return nopVerifier;
	}

}
