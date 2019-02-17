package de.rub.nds.oidc.utils;

/**
  * Copied from the package
  * eu.dety.burp.joseph.attacks.key_confusion, which is part of
  * "JOSEPH - JavaScript Object Signing and Encryption Pentesting Helper"
  * by Dennis Detering (see https://github.com/RUB-NDS/JOSEPH) and has been
  * licensed under GPL2
  */

// Types of payload variation
public enum KeyConfusionPayloadType {
	// Derived from PEM input
	ORIGINAL,
	ORIGINAL_NO_HEADER_FOOTER,
	ORIGINAL_NO_LF,
	ORIGINAL_NO_HEADER_FOOTER_LF,
	ORIGINAL_ADDITIONAL_LF,

	PKCS1,
	PKCS1_NO_HEADER_FOOTER,
	PKCS1_NO_LF,
	PKCS1_NO_HEADER_FOOTER_LF,

	// Derived from JWK input
	PKCS8,
	PKCS8_WITH_HEADER_FOOTER,
	PKCS8_WITH_LF,
	PKCS8_WITH_HEADER_FOOTER_LF,
	PKCS8_WITH_HEADER_FOOTER_LF_ENDING_LF,
}

