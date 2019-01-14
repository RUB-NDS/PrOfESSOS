package de.rub.nds.oidc.browser.op;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import de.rub.nds.oidc.browser.BrowserSimulator;
import de.rub.nds.oidc.server.rp.RPContextConstants;
import de.rub.nds.oidc.server.rp.RPType;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

public abstract class AbstractOPBrowser extends BrowserSimulator {

	protected String submitScript;
	protected String consentScript;
	protected String userName;
	protected String userPass;

    protected void evalScriptTemplates() {
		// todo maybe this should be part of the browser initializaiton?
		String templateString;

		try {

			if (!Strings.isNullOrEmpty(opConfig.getLoginScript())) {
				// Todo: check for input field name and run template engine
				templateString = opConfig.getLoginScript();
			} else {
				templateString = IOUtils.toString(getClass().getResourceAsStream("/login-form.st"), "UTF-8");
			}

			opConfig.setLoginScript(templateString); // TODO: if we add an inputfieldname, we need to evaluate this once
			Map<String,String> user = ImmutableMap.of("current_user_username", userName,"current_user_password", userPass);
			submitScript = te.eval(Maps.newHashMap(user), templateString);

			if (!Strings.isNullOrEmpty(opConfig.getConsentScript())) {
				consentScript = opConfig.getConsentScript();
			} else {
				// TODO: currently using consent script as is, not a template
				consentScript = IOUtils.toString(getClass().getResourceAsStream("/consent-form.st"), "UTF-8");
			}
			opConfig.setConsentScript(consentScript);

		} catch (IOException e) {
			logger.log("Scripttemplate evaluation failed");
			throw new RuntimeException( new InterruptedException(e.getMessage()));
		}

    }


    protected AuthenticationRequest getAuthnReq(RPType rpType) {
		URI authnReq;
		authnReq = RPType.HONEST.equals(rpType)
				? (URI) stepCtx.get(RPContextConstants.RP1_PREPARED_AUTHNREQ)
					: (URI) stepCtx.get(RPContextConstants.RP2_PREPARED_AUTHNREQ);
		try {
			AuthenticationRequest req = AuthenticationRequest.parse(authnReq);
			return req;
		} catch (ParseException e) {
			logger.log("Error parsing generated AuthenticationRequest URI", e);
			return null;
		}
	}


}
