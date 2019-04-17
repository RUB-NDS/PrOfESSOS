package de.rub.nds.oidc.browser.op;

import de.rub.nds.oidc.server.rp.RPContextConstants;
import de.rub.nds.oidc.test_model.TestStepResult;
import org.apache.commons.lang3.StringUtils;

import java.net.URLDecoder;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static de.rub.nds.oidc.server.rp.RPImplementation.REDIRECT_PATH;

public class OPRumBrowser extends SingleRpSingleUserAuthRunner {
	private boolean checkUrlOnTimeout = true;

	@Override
	protected TestStepResult waitForRPResult() throws InterruptedException {

		try {
			// wait for TestStepResult from RP
			return blockAndResult.get(SHORT_WAIT_TIMEOUT, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			if (checkUrlOnTimeout) {
				// most likely the request to the manipulated redirect_uri failed (invalid subdomain, TLD, or userinfo part)
				// check for manipulated subdomain in Browser URI (might be malformed due to userinfo-part and encoding, so
				// we can not use standard URL parser/tools)
				String manipulator = (String) stepCtx.get(RPContextConstants.REDIRECT_URI_MANIPULATOR);
				if (manipulator != null || finalUrl.matches(".*\\..+\\." + RPContextConstants.INVALID_TLD)) {
					logger.log("Redirect to manipulated redirect_uri detected.");
				}

				// URL may be invalid, e.g., due to malformed userinfo part. Nevertheless, OP redirected to an unregistered
				// redirect_uri, so we assume that the callback somehow reaches the attacker (and forward to the fixed URL).
				// Note: this wont work for form_post response mode. However, form_post is currently not used/registered in RUM tests.
				if (finalUrl.matches(".*[&#?](code|token|id_token)=.*")) {
					logger.log("Tokens/AuthCode found in (manipulated) callback URI.");
					int indQuest = finalUrl.indexOf("?");
					int indHash = finalUrl.indexOf("#");
					int start = indQuest > -1 ? indQuest : indHash;
					String url = opConfig.getHonestRpResourceId() + "/" + REDIRECT_PATH + finalUrl.substring(start);

					// overwrite old futures
					initRPLocks();
					checkUrlOnTimeout = false;
					stepCtx.put(RPContextConstants.REDIRECT_URI_MANIPULATOR, "trigger for rp");
					driver.get(url);
					confirmBrowserFinished();
					return waitForRPResult();
				}
			}
			logger.log("Browser Timeout while waiting for RP");
			logger.log("Authentication failed, assuming test passed.");
			return TestStepResult.PASS;

			// TODO: add seleniumProxy (BrowserMob) and intercept DNS/HTTP for manipulated URIs? This would also 
			// allow to capture code/token submitted to manipulated redirect_uri when using form_post response mode
		} catch (ExecutionException e) {
			logger.log("Unknown error waiting for RP result:", e);
			return TestStepResult.UNDETERMINED;
		}
	}

	@Override
	protected boolean consentRequired(String browserUrl) {
		String req = getAuthnReqString(getStartRpType());
		String[] rus = Arrays.stream(req.split("&"))
				.filter(s -> s.startsWith("redirect_uri="))
				.map(s -> URLDecoder.decode(s.substring(13)).toLowerCase())
				.toArray(String[]::new);

		if (StringUtils.startsWithAny(browserUrl.toLowerCase(), rus)) {
			logger.log("No consent page encountered in browser");
			return false;
		}
		return true;
	}
}
