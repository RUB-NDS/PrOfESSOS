package de.rub.nds.oidc.browser;

import com.google.common.base.Strings;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import de.rub.nds.oidc.server.op.OPContextConstants;
import de.rub.nds.oidc.test_model.TestStepResult;
import de.rub.nds.oidc.utils.UnsafeJSONObject;
import net.minidev.json.JSONObject;
import org.openqa.selenium.By;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class IDSpoofingRPBrowser extends DefaultRPTestBrowser {

	@Override
	protected TestStepResult checkConditionOnFinalPage() {
		Object userInfoJson = stepCtx.get(OPContextConstants.STORED_USERINFO_RESPONSE_EVIL);
		if (!(userInfoJson instanceof UnsafeJSONObject)) {
			logger.logCodeBlock(userInfoJson.toString(), "Unexpected content:");
			return TestStepResult.UNDETERMINED;
		}

		try {
			UnsafeJSONObject uiJson = (UnsafeJSONObject) userInfoJson;
			String uiSub = uiJson.getAsString("sub");
			SignedJWT idToken = (SignedJWT) stepCtx.get(OPContextConstants.STORED_ID_TOKEN_EVIL);
			JWTClaimsSet idtClaims = idToken.getJWTClaimsSet();
			String idtSub = idtClaims.getSubject();
			
			// RP must check that sub claims in ID Token and UserInfo response are the same
			if (!uiSub.equals(idtSub) || Strings.isNullOrEmpty(idtSub)) {
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
					logger.logCodeBlock(String.join(" and ", usedClaims),
							"Detected use of invalid UserInfo claim(s):");
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
