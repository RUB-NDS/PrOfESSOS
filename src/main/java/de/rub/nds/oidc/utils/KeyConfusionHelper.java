package de.rub.nds.oidc.utils;

import org.apache.commons.codec.binary.Base64;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.security.PublicKey;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * This class implements some conversion functions needed to perform
 * the key confusion attacks. The functions included in this class are
 * (mostly) verbatim copies of the KeyConfusionInfo class from the package
 * eu.dety.burp.joseph.attacks.key_confusion, which is part of
 * "JOSEPH - JavaScript Object Signing and Encryption Pentesting Helper"
 * by Dennis Detering (see https://github.com/RUB-NDS/JOSEPH) and has been
 * licensed under GPL2
 */
public class KeyConfusionHelper {



	//
	public static String transformKeyByPayload(Enum payloadTypeId, String key) {
		String modifiedKey;

		switch ((KeyConfusionPayloadType) payloadTypeId) {
			case ORIGINAL_NO_HEADER_FOOTER:
				modifiedKey = key.replace("-----BEGIN PUBLIC KEY-----\n", "").replaceAll("-----END PUBLIC KEY-----\\n?", "")
						.replace("-----BEGIN RSA PUBLIC KEY-----\n", "").replaceAll("-----END RSA PUBLIC KEY-----\\n?", "");
				break;

			case ORIGINAL_NO_LF:
				modifiedKey = key.replaceAll("\\r\\n|\\r|\\n", "");
				break;

			case ORIGINAL_NO_HEADER_FOOTER_LF:
				modifiedKey = transformKeyByPayload(KeyConfusionPayloadType.ORIGINAL_NO_LF, transformKeyByPayload(KeyConfusionPayloadType.ORIGINAL_NO_HEADER_FOOTER, key));
				break;

			case ORIGINAL_ADDITIONAL_LF:
				modifiedKey = key + "\n";
				break;

			case PKCS1:
				modifiedKey = key.substring(32);
				break;

			case PKCS1_NO_HEADER_FOOTER:
				modifiedKey = transformKeyByPayload(KeyConfusionPayloadType.PKCS1, transformKeyByPayload(KeyConfusionPayloadType.ORIGINAL_NO_HEADER_FOOTER, key));
				break;

			case PKCS1_NO_LF:
				modifiedKey = transformKeyByPayload(KeyConfusionPayloadType.PKCS1, transformKeyByPayload(KeyConfusionPayloadType.ORIGINAL_NO_LF, key));
				break;

			case PKCS1_NO_HEADER_FOOTER_LF:
				modifiedKey = transformKeyByPayload(KeyConfusionPayloadType.PKCS1, transformKeyByPayload(KeyConfusionPayloadType.ORIGINAL_NO_HEADER_FOOTER_LF, key));
				break;

			case ORIGINAL:
			default:
				modifiedKey = key;
				break;

		}

		return modifiedKey;
	}

	// takes Java PKCS8 pubKey
	public static String transformKeyByPayload(Enum KeyConfusionPayloadTypeId, PublicKey key) throws UnsupportedEncodingException {
		Base64 base64Pem = new Base64(64, "\n".getBytes("UTF-8"));

		String modifiedKey;

		switch ((KeyConfusionPayloadType) KeyConfusionPayloadTypeId) {

			case PKCS8_WITH_HEADER_FOOTER:
				modifiedKey = "-----BEGIN PUBLIC KEY-----" + Base64.encodeBase64String(key.getEncoded()) + "-----END PUBLIC KEY-----";
				break;

			case PKCS8_WITH_LF:
				modifiedKey = base64Pem.encodeToString(key.getEncoded());
				break;

			case PKCS8_WITH_HEADER_FOOTER_LF:
				modifiedKey = "-----BEGIN PUBLIC KEY-----\n" + base64Pem.encodeToString(key.getEncoded()) + "-----END PUBLIC KEY-----";
				break;

			case PKCS8_WITH_HEADER_FOOTER_LF_ENDING_LF:
				modifiedKey = transformKeyByPayload(KeyConfusionPayloadType.PKCS8_WITH_HEADER_FOOTER_LF, key) + "\n";
				break;

			case PKCS8:
			default:
				modifiedKey = Base64.encodeBase64String(key.getEncoded());
				break;
		}

		return modifiedKey;
	}

	
	public static byte[] generateMac(String algorithm, byte[] key, byte[] message) {
		try {
			Mac mac = Mac.getInstance(algorithm);
			SecretKeySpec secret_key = new SecretKeySpec(key, algorithm);
			mac.init(secret_key);

			return mac.doFinal(message);
		} catch (Exception e) {
			return null;
		}
	}
	
	// source: https://stackoverflow.com/questions/7611383/generating-rsa-keys-in-pkcs1-format-in-java
	public static String convertPKCS8toPKCS1PemString(PublicKey key)  {
		byte[] pubBytes = key.getEncoded();
		try {
			SubjectPublicKeyInfo spkInfo = SubjectPublicKeyInfo.getInstance(pubBytes);
			ASN1Primitive primitive = spkInfo.parsePublicKey();
			byte[] publicKeyPKCS1 = primitive.getEncoded();

			PemObject pemObject = new PemObject("RSA PUBLIC KEY", publicKeyPKCS1);
			StringWriter stringWriter = new StringWriter();
			PemWriter pemWriter = new PemWriter(stringWriter);
			pemWriter.writeObject(pemObject);
			pemWriter.close();
			String pemString = stringWriter.toString();

			return pemString;
		} catch (IOException e) {
			return null;
		}
	}
}
