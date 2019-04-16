package de.rub.nds.oidc.server.rp;

import com.google.common.base.Strings;
import com.nimbusds.jwt.JWT;
import com.nimbusds.openid.connect.sdk.ClaimsRequest;
import com.nimbusds.openid.connect.sdk.claims.ClaimRequirement;
import de.rub.nds.oidc.test_model.TestStepResult;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.text.ParseException;
import java.util.Arrays;

import static de.rub.nds.oidc.server.rp.RPParameterConstants.*;

public class SubClaimSpoofingRP extends DefaultRP {

	@Override
	protected ClaimsRequest getAuthReqClaims() {
		// template evaluation is performed in browser, as RP does not necessarily know
		// the sub values when generating the AuthnReq
		final String user1sub = "§placeholder_user1_sub§";
		final String user2sub = "§placeholder_user2_sub§";

		ClaimsRequest claims = new ClaimsRequest();
		if (params.getBool(AUTHNREQ_CLAIMSREQ_SUB1)) {
			claims.addIDTokenClaim("sub", ClaimRequirement.ESSENTIAL, null, user1sub);
		}
		if (params.getBool(AUTHNREQ_CLAIMSREQ_ARRAY_SUB1)) {
			claims.addIDTokenClaim("sub", ClaimRequirement.ESSENTIAL, null, Arrays.asList(user1sub));
		}
		if (params.getBool(AUTHNREQ_CLAIMSREQ_ARRAY_SUB1_SUB2)) {
			claims.addIDTokenClaim("sub", ClaimRequirement.ESSENTIAL, null, Arrays.asList(user1sub, user2sub));
		}
		if (params.getBool(AUTHNREQ_CLAIMSREQ_ARRAY_SUB2_SUB1)) {
			claims.addIDTokenClaim("sub", ClaimRequirement.ESSENTIAL, null, Arrays.asList(user2sub, user1sub));
		}
		
		return claims;
	}

	protected URI applyIdTokenHintToAuthReqUri(URI uri) {
		final String user1TokenHint = "§placeholder_user1_idtoken§";
		if (params.getBool(AUTHNREQ_IDTOKEN_HINT_USER1)) {
			UriBuilder ub = UriBuilder.fromUri(uri);
			ub.queryParam("id_token_hint", user1TokenHint);
			return ub.build();
		}
		// default: do nothing
		return uri;
	}

	@Override
	protected TestStepResult checkIdTokenCondition(JWT idToken) {
		try {
			String idtSub = idToken.getJWTClaimsSet().getSubject();
			String user1Sub = (String) stepCtx.get(RPContextConstants.STORED_USER1_SUB_VAL);
			boolean claimsSupported = opMetaData.supportsClaimsParam();

			if (Strings.isNullOrEmpty(user1Sub)) {
				logger.log("Reference value for sub claim not found.");
				return TestStepResult.UNDETERMINED;
			}
			if (idtSub.equals(user1Sub)) {
				logger.log(String.format("ID Token contains sub claim of User1: %s. Assuming test failed.", user1Sub));
				return TestStepResult.FAIL;
			}

			logger.log(String.format("User1 sub (%s) not found in ID Token, token contained sub: %s." +
					"%nClaims request parameter supported by OP: %s", user1Sub, idtSub, claimsSupported));

			if (params.getBool(AUTHNREQ_CLAIMSREQ_ARRAY_SUB1_SUB2) || params.getBool(AUTHNREQ_CLAIMSREQ_ARRAY_SUB2_SUB1)) {
				return TestStepResult.PASS;
			}
			if (claimsSupported || params.getBool(AUTHNREQ_IDTOKEN_HINT_USER1)) {
				// spec violation
				logger.log("As per Step 4 of " +
						"<a href=\"https://openid.net/specs/openid-connect-core-1_0.html#AuthRequestValidation\" target=\"_blank\">https://openid.net/specs/openid-connect-core-1_0.html#AuthRequestValidation</a> " +
						"the OP must not reply with an ID Token for a different user if <code>id_token_hint</code> or " +
						"<code>claims</code> request parameters are used.");
				return TestStepResult.UNDETERMINED;
			}
			return TestStepResult.PASS;
		} catch (ParseException e) {
			logger.logCodeBlock(idToken.getParsedString(), "Invalid ID Token received:");
			logger.log("Exception was", e);
			return TestStepResult.UNDETERMINED;
		}
	}
}

