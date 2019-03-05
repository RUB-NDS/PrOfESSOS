package de.rub.nds.oidc.server.rp;

import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.pkce.CodeChallenge;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;

import javax.annotation.Nullable;

import static de.rub.nds.oidc.server.rp.RPParameterConstants.*;

public class PkceRP extends DefaultRP {
	
	@Override
	@Nullable
	protected CodeChallengeMethod getCodeChallengeMethod(){
		CodeChallengeMethod cm = null;
		if (params.getBool(AUTHNREQ_PKCE_METHOD_PLAIN)) {
			cm = CodeChallengeMethod.PLAIN;
		}
		if (params.getBool(AUTHNREQ_PKCE_METHOD_S_256)) {
			cm = CodeChallengeMethod.S256;
		}
		
		return cm;
	}

	@Override
	@Nullable
	protected CodeVerifier getCodeChallengeVerifier() {
		CodeVerifier verifier = null;
		if (params.getBool(AUTHNREQ_PKCE_METHOD_PLAIN) || params.getBool(AUTHNREQ_PKCE_METHOD_S_256) || params.getBool(AUTHNREQ_PKCE_METHOD_EXCLUDED)) {
			verifier = new CodeVerifier();
			// store for later
			if (!params.getBool(TOKENREQ_PKCE_FROM_OTHER_SESSION)) {
				stepCtx.put(RPContextConstants.STORED_PKCE_VERIFIER, verifier);
			}
		}
		return verifier;
	}

	@Override
	protected void tokenRequestApplyPKCEParams(HTTPRequest req) {
		String encodedQuery = req.getQuery();

		StringBuilder sb = new StringBuilder();
		sb.append(encodedQuery);
		if (params.getBool(TOKENREQ_ADD_PKCE_METHOD_PLAIN)){
			// attempt "downgrade", use code_challenge from AuhtnReq and 
			// add plain as code_challenge_method (invalid per RFC7636)
			CodeChallenge cc = CodeChallenge.compute(getCodeChallengeMethod(), getStoredPKCEVerifier());
			sb.append("&code_challenge_method=plain&code_verifier=");
			sb.append(cc.getValue());
		}
		if (params.getBool(TOKENREQ_PKCE_EXCLUDED)) {
			req.setQuery(sb.toString());
			return;
		}
		if (params.getBool(TOKENREQ_PKCE_INVALID)){
			// attempt downgrade in tokenreq, invalid per RFC7636
			sb.append("&code_verifier=");

			CodeVerifier cv = getStoredPKCEVerifier();
			String verifier = cv.getValue();
			// change last char
			// only certain ASCII chars are allowed; to keep it simple, use A or B
			String last = verifier.endsWith("A") ? "B" : "A";
			sb.append(verifier.substring(0, verifier.length()-1) + last);
		} else {
			CodeVerifier cv = getStoredPKCEVerifier();
			if (cv != null) {
				sb.append("&code_verifier=");
				sb.append(cv);
			}
		}

		req.setQuery(sb.toString());
	}


}
