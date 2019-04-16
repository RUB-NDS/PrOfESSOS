package de.rub.nds.oidc.server.rp;

import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse;
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

public class RumRP extends DefaultRP {


	@Override
	public void callback(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException, URISyntaxException, ParseException {

		CompletableFuture<TestStepResult> browserBlocker = (CompletableFuture<TestStepResult>) stepCtx.get(RPContextConstants.BLOCK_BROWSER_AND_TEST_RESULT);
		AuthenticationResponse response = processCallback(req, resp, path);
		String manipulator = (String) stepCtx.get(RPContextConstants.REDIRECT_URI_MANIPULATOR);
		TestStepResult result = TestStepResult.UNDETERMINED;  // OP should not redirect the enduser to an unregistered redirect_uri

		if (!response.indicatesSuccess()) {
			// callback received with error response
			if (type.equals(RPType.EVIL)) {
				if (manipulator != null) {
					logger.log("Authentication ErrorResponse sent to manipulated redirect_uri.");
					// TODO: if oidc => fail, if oauth => undetermined ?
					browserBlocker.complete(TestStepResult.FAIL);
					return;
				}
				logger.log("Authentication ErrorResponse received at redirect_uri registered with Evil Client");
				browserBlocker.complete(TestStepResult.UNDETERMINED);
				return;
			}

			logger.log("AuthenticationResponse Error received in Honest Client");
			logger.logHttpRequest(req, response.toString());
			browserBlocker.complete(TestStepResult.PASS);
			return;
		}

		// callback received with successful authentication response incl. code/token
		String client = type.equals(RPType.HONEST) ? "Honest Client" : "Evil Client";
		logger.log("Authentication SuccessResponse received in " + client);
		logger.logHttpRequest(req, null);
		if (params.getBool(AUTHNREQ_RU_PP_EVIL_FIRST) || params.getBool(AUTHNREQ_RU_PP_HONEST_FIRST)) {
			// valid redirect_uri was part of AuthnReq. PASS, unless we recognize that 
			// the request was made to a manipulated uri later on

			result = isStartRP() ? TestStepResult.PASS : TestStepResult.FAIL;
		}

		if (manipulator != null || params.getBool(AUTHNREQ_HONEST_USERPART_REDIRURI)) {
			// OP redirected to a manipulated (possibly) invalid URI. To allow further processing,
			// the browser re-submitted the request to the original (un-tampered) callback url
			logger.log("Authentication Response submitted to manipulated redirect_uri, Authorization Server does " +
					"not perform exact string matching on redirect_uri.");
			result = TestStepResult.FAIL;
			// TODO: only fail for OIDC as OAUTH does not require exact matching (?)
		}

		// try to redeem the code
		AuthenticationSuccessResponse successResponse = response.toSuccessResponse();
		if (successResponse.getAuthorizationCode() != null) {
			// try to redeem authorization code
			TokenResponse tokenResponse = redeemAuthCode(successResponse.getAuthorizationCode());
			if (!tokenResponse.indicatesSuccess()) {
				// Code redemption failed, e.g., because OP detected manipulated redirect_uri in Token Request
				// Error messages have been logged already
				result = result.equals(TestStepResult.UNDETERMINED) ? TestStepResult.PASS : result;
			} else {
				if (params.getBool(TOKEN_RECEIVAL_FAILS_TEST)) {
					logger.log("AuthorizationCode successfully redeemed, assuming test failed.");
					browserBlocker.complete(TestStepResult.FAIL);
					return;
				}
			}
		}
		// also fail if token received in implicit flow
		if (params.getBool(TOKEN_RECEIVAL_FAILS_TEST) || manipulator != null) {
			if (successResponse.getIDToken() != null) {
				result = TestStepResult.FAIL;
				logger.log("ID Token received at manipulated redirect_uri");
			}
			if (successResponse.getAccessToken() != null) {
				result = TestStepResult.FAIL;
				logger.log("Access Token received at manipulated redirect_uri");
			}
		}

		browserBlocker.complete(result);
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
						+ URLEncoder.encode(tmp[1], "utf-8");
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
		if (!includeScheme) {
			userInfoPart = userInfoPart.replaceFirst(malicious.getScheme() + "://", "");
		}
		if (urlEncodeUserInfo) {
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
