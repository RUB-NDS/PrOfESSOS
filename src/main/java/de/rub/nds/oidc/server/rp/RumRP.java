package de.rub.nds.oidc.server.rp;

import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.AuthenticationResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.test_model.TestStepResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.concurrent.CompletableFuture;

import static de.rub.nds.oidc.server.rp.RPParameterConstants.*;
import static de.rub.nds.oidc.server.rp.RPParameterConstants.AUTHNREQ_FORCE_EVIL_REDIRURI;

public class RumRP extends DefaultRP {


	@Override
	public void callback(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException, URISyntaxException, ParseException {

		CompletableFuture<TestStepResult> browserBlocker = (CompletableFuture<TestStepResult>) stepCtx.get(RPContextConstants.BLOCK_BROWSER_AND_TEST_RESULT);

		AuthenticationResponse response = processCallback(req, resp, path);

		String manipulator = (String) stepCtx.get(RPContextConstants.REDIRECT_URI_MANIPULATOR);

		// callback received with error response
		if (!response.indicatesSuccess()) {
			if (type.equals(RPType.EVIL)) {
				logger.log("Authentication ErrorResponse received in Evil Client");
				logger.log("This may indicate an open redirector that could be chained to leak AuthCodes - please check manually.");
				browserBlocker.complete(TestStepResult.UNDETERMINED);
				return;
			}
			if (manipulator != null && req.getRequestURL().toString().contains(manipulator)) {
				logger.log("Authentication ErrorResponse sent to manipulated redirect_uri.");
				// TODO: if oidc => fail, if oauth => undetermined
				browserBlocker.complete(TestStepResult.FAIL);
				return;
			}

			logger.log("AuthenticationResponse Error received in Honest Client");
			logger.logHttpRequest(req, response.toString());
			browserBlocker.complete(TestStepResult.PASS);
			return;
		}

		// callback received with successful authentication response incl. code/token
		if (type.equals(RPType.EVIL)) {
			// received auth code in evil client
			logger.log("Authentication SuccessResponse received in Evil Client");
			logger.logHttpRequest(req, null);
			browserBlocker.complete(TestStepResult.FAIL);
			return;
		}

		logger.log("Authentication SuccessResponse received in Honest Client");
		logger.logHttpRequest(req, null);


		if (manipulator != null && req.getRequestURL().toString().contains(manipulator)) {
			logger.log("Authentication Response sent to manipulated redirect_uri.");
			logger.log("Authorization Server does not perform exact string matching on redirect_uri.");
			// TODO: only fail for OIDC as OAUTH does not require exact matching afaik
			browserBlocker.complete(TestStepResult.FAIL);
			return;
		}


		// in codeHijacking tests, try to redeem the code
		AuthenticationSuccessResponse successResponse = response.toSuccessResponse();
		if (successResponse.impliedResponseType().impliesCodeFlow()) {
			// attempt code redemption
			TokenResponse tokenResponse = redeemAuthCode(successResponse.getAuthorizationCode());
			if (!tokenResponse.indicatesSuccess()) {
				// code redemption failed, error messages have been logged already
				browserBlocker.complete(TestStepResult.PASS);
				return;
			}

			if (params.getBool(SUCCESSFUL_CODE_REDEMPTION_FAILS_TEST)) {
				logger.log("AuthorizationCode successfully redeemed, assuming test failed.");
				browserBlocker.complete(TestStepResult.FAIL);
				return;
			}

			browserBlocker.complete(TestStepResult.PASS);
		}
	}


	@Override
	public void prepareAuthnReq() {

		super.prepareAuthnReq();
		String orig = getStoredAuthnReqString();

		String authnReqString = applyRedirectUriToAuthReq(orig);

		// make prepared request available for the browser
		String currentRP = type == RPType.HONEST ? RPContextConstants.RP1_PREPARED_AUTHNREQ
				: RPContextConstants.RP2_PREPARED_AUTHNREQ;
		stepCtx.put(currentRP, authnReqString);
	}


	protected String applyRedirectUriToAuthReq(String authnRequest) {
		String result = "";
		try {
			String placeholder = "http%3A%2F%2Fplaceholder.uri";
			// TODO: also match lowercase (%3a%2f%2f)

			String redirectUri = getManipulatedAuthReqRedirectUri();
			String encoded;
			if (redirectUri.contains("&redirect_uri=")) {
				// in parameter pollution testcases, do not url encode the parameter key
				String[] tmp = redirectUri.split("&redirect_uri=");
				encoded = URLEncoder.encode(tmp[0], "utf-8")
						+ "&redirect_uri="
						+ URLEncoder.encode(tmp[1], "utf-8") ;
			} else {
				encoded = URLEncoder.encode(redirectUri, "utf-8");
			}
			result = authnRequest.replace(placeholder, encoded);
		} catch (UnsupportedEncodingException e) {
			// utf-8 should never raise this
		}

		return result;
	}


	@Override
	protected URI getAuthReqRedirectUri() {
		URI redirectPlaceholder = null;
		try {
			redirectPlaceholder = new URI("http://placeholder.uri");
		} catch (URISyntaxException e) {
			// must not happen.
		}
		return redirectPlaceholder;
	}


	protected String getManipulatedAuthReqRedirectUri() {
		URI redirUri = params.getBool(AUTHNREQ_FORCE_EVIL_REDIRURI) ? getEvilRedirectUri() : getRedirectUri();
		URI redirUriBenign = !params.getBool(AUTHNREQ_FORCE_EVIL_REDIRURI) ? getEvilRedirectUri() : getRedirectUri();

		boolean subdom = params.getBool(AUTHNREQ_ADD_SUBDOMAIN_REDIRURI);
		boolean path = params.getBool(AUTHNREQ_ADD_PATHSUFFIX_REDIRURI);
		boolean tld = params.getBool(AUTHNREQ_ADD_INVALID_TLD);
		if (subdom || path || tld) {
			URI uri;
			uri = manipulateURI(redirUri, subdom, path, tld);
			return uri.toString();
		}

		String uriString = redirUri.toString();
		if (params.getBool(AUTHNREQ_HONEST_USERPART_REDIRURI)) {
			uriString = userInfoRedirectUri(redirUri, redirUriBenign);
		} else if (params.getBool(AUTHNREQ_RU_PP_HONEST_FIRST)) {
			uriString = redirUriBenign.toString() + "&redirect_uri=" + redirUri.toString();
		} else if (params.getBool(AUTHNREQ_RU_PP_EVIL_FIRST)) {
			uriString = redirUri.toString() + "&redirect_uri=" + redirUriBenign.toString();
		}

		return uriString;
	}

	private String userInfoRedirectUri(URI malicious, URI benign) {

		// Place the correct redirectURI in the first field of the userinfo part
		// of the URI, i.e.: https://honest:pass@evil.com
		// Note that rfc3986#section-3.2.1 allows an arbitrary number of colon separated
		// userinfo parts preceding the authority section. That is, even inclusion of
		// unencoded URIs that contain a port should result in a valid URI of the form:
		// <scheme>://<userinfo1>:<userinfo2>:...:<userinfoN>@<authority>/<path>?<query>#<fragment>
		// javax.ws.rs.core.UriBuilder throws when using a URL in userInfoPart.
		// apache.httpclient.client.utils.URIBuilder URL encodes the userinfoPart
		// which is correct but can not be disabled. Therefore, the URI is manually
		// crafted from strings
		String uriResult;
		boolean includeScheme = !params.getBool(USERINFOPART_EXCLUDE_SCHEME);
		boolean urlEncodeUserInfo = params.getBool(USERINFOPART_URL_ENCODE);

		String userInfoPart = malicious.toString();
		if(!includeScheme) {
			userInfoPart = userInfoPart.replaceFirst(malicious.getScheme() + "://", "");
		}
		if(urlEncodeUserInfo) {
			try {
				// applies only to the username part
				userInfoPart = URLEncoder.encode(userInfoPart, "utf-8");
			} catch (UnsupportedEncodingException e) {
				// utf-8 should always be available
			}
		}

		// always append an unencoded colon and @, to mark the begin of the URL's authority section
		userInfoPart += ":pass@";

		// perform actual manipulation of the target URI
		String scheme = benign.getScheme() + "://";
		uriResult = benign.toString().replaceFirst(scheme, "");
		uriResult = scheme + userInfoPart + uriResult;
		logger.logCodeBlock(uriResult, "Using Redirect URI");

		return uriResult;

		// TODO: multiple @ characters might let different URL parsers choose different URL parts
	}


}
