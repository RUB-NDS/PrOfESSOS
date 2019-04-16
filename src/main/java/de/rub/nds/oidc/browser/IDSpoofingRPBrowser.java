package de.rub.nds.oidc.browser;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import de.rub.nds.oidc.server.op.OPContextConstants;
import de.rub.nds.oidc.test_model.TestStepResult;
import de.rub.nds.oidc.utils.UnsafeJSONObject;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.tuple.Pair;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class IDSpoofingRPBrowser extends DefaultRPTestBrowser {

	@Override
	protected TestStepResult checkConditionOnFinalPage() {
		Object userInfoJson = stepCtx.get(OPContextConstants.STORED_USERINFO_RESPONSE_EVIL);
		if (!(userInfoJson instanceof UnsafeJSONObject)) {
			logger.logCodeBlock("Failed to check final condition, unexpected content:", userInfoJson.toString());
			return null;
		}
		
		UnsafeJSONObject uiJson = (UnsafeJSONObject) userInfoJson;
		List<Pair<String, Object>> subs = uiJson.get("sub");
		if (subs.size() == 1 && subs.get(0).getValue() instanceof JSONArray
				|| subs.size() > 1) {
			// rely on standard user-needle search, if multiple sub claims
			return null;
		}

		// This is more a compliance check than an attack:
		// RP must check that sub claims in ID Token and UserInfo response are the same and
		// must not use claims from the userinfo response otherwise.
		try {
			String uiSub = uiJson.getAsString("sub");
			SignedJWT idToken = (SignedJWT) stepCtx.get(OPContextConstants.STORED_ID_TOKEN_EVIL);
			JWTClaimsSet idtClaims = idToken.getJWTClaimsSet();
			String idtSub = idtClaims.getSubject();
			
			if (!idtSub.equals(uiSub)) {
				// check if page contains unvalidated UserInfo claims
				Collection<Object> uiClaimValues = uiJson.values();
				Collection<Object> idtClaimValues = idtClaims.getClaims().values();
				uiClaimValues.remove(idtClaimValues);

				List<String> usedClaims = new ArrayList<>();
				for (Object claim : uiClaimValues) {
					String searchString = claim.toString();
					if (searchOnPage(searchString)) {
						usedClaims.add(searchString);
					}
				}
				if (!usedClaims.isEmpty()) {
					logger.logCodeBlock("Detected use of invalid UserInfo claim(s):", String.join(", ", usedClaims)
					);
					return TestStepResult.FAIL;
				}
			}
		} catch (ParseException e) {
			logger.log("Failed to parse ID Token claims.");
			return TestStepResult.UNDETERMINED;
		}
		// proceed with normal checks
		return null;
	}
}
