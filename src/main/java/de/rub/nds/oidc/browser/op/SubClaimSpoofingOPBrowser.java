package de.rub.nds.oidc.browser.op;

import com.nimbusds.jwt.JWT;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import de.rub.nds.oidc.server.rp.RPContextConstants;
import de.rub.nds.oidc.server.rp.RPType;
import de.rub.nds.oidc.test_model.TestStepResult;
import org.apache.http.client.utils.URIBuilder;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.text.ParseException;
import java.util.HashMap;

import static de.rub.nds.oidc.server.rp.RPContextConstants.*;

public class SubClaimSpoofingOPBrowser extends AbstractOPBrowser {
	private URI authnReqUri;

	@Override
	public TestStepResult run() throws InterruptedException {
		try {
			authnReqUri = (URI) stepCtx.get(RP1_PREPARED_AUTHNREQ);

			String user1Sub = getUserSubject(true);
			String user2Sub = getUserSubject(false);
			// store reference 
			stepCtx.put(RPContextConstants.STORED_USER1_SUB_VAL, user1Sub);

			// currently, there is no way to be sure that the RP impl knows
			// the correct sub values when constructing the AR, so we
			// need to replace the sub claim placeholders
			HashMap<String, Object> ctx = new HashMap<>();
			ctx.put("placeholder_user1_sub", user1Sub);
			ctx.put("placeholder_user2_sub", user2Sub);
			String idtString = ((JWT) suiteCtx.get(STORED_USER1_IDTOKEN)).serialize();
			ctx.put("placeholder_user1_idtoken", idtString);

			String query = te.eval(ctx, authnReqUri.getQuery());
			URI arWithClaims = new URIBuilder(authnReqUri)
					.setCustomQuery(
							URLDecoder.decode(query, "utf-8")  // setCustomQuery() applies URL encoding
					).build();
			stepCtx.put(RP1_PREPARED_AUTHNREQ, arWithClaims);
			userName = opConfig.getUser2Name();
			userPass = opConfig.getUser2Pass();

			TestStepResult res = runUserAuth(RPType.HONEST);
			return res;
		} catch (URISyntaxException | UnsupportedEncodingException e) {
			throw new InterruptedException("URI encoding of Authentication Request failed.");
		}
	}


	private String getUserSubject(boolean isUser1) throws InterruptedException {
		// use sub from  old idToken, if there is already one stored in suiteCtx
		JWT usertoken = (JWT) suiteCtx.get(isUser1 ? STORED_USER1_IDTOKEN : STORED_USER2_IDTOKEN);
		if (usertoken != null) {
			try {
				return usertoken.getJWTClaimsSet().getSubject();
			} catch (ParseException e) {
				throw new InterruptedException("Invalid IdToken read from test-suite context");
			}
		}
		// otherwise, run authentication so that a new id_token is stored in suiteCtx
		String localUserName = isUser1 ? opConfig.getUser1Name() : opConfig.getUser2Name();
		String localUserPass = isUser1 ? opConfig.getUser1Pass() : opConfig.getUser2Pass();
		stepCtx.put(CURRENT_USER_USERNAME, localUserName);
		stepCtx.put(CURRENT_USER_PASSWORD, localUserPass);

		try {
			AuthenticationRequest.Builder ab = new AuthenticationRequest.Builder(
					AuthenticationRequest.parse((URI) stepCtx.get(RP1_PREPARED_AUTHNREQ)));
			// make sure no claims are requested in this request (we only want to learn the 
			// sub value for use in later claimsrequests)
			ab.claims(null);
			AuthenticationRequest arNoClaims = ab.build();
			stepCtx.put(RP1_PREPARED_AUTHNREQ, arNoClaims);

		} catch (com.nimbusds.oauth2.sdk.ParseException e) {
			throw new InterruptedException("Found invalid Authentication Request in stepCtx");
		}
		runUserAuth(RPType.HONEST);
		driver.manage().deleteAllCookies();

		return getUserSubject(isUser1);
	}


}
