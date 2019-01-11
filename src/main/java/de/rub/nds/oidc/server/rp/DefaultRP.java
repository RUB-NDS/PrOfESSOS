package de.rub.nds.oidc.server.rp;

import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.openid.connect.sdk.AuthenticationErrorResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationResponseParser;
import de.rub.nds.oidc.server.RequestPath;
import de.rub.nds.oidc.test_model.TestStepResult;
import jdk.nashorn.internal.runtime.regexp.joni.exception.SyntaxException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;

public class DefaultRP extends AbstractRPImplementation {

    @Override
    public void callback(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException, URISyntaxException, ParseException {
        // TODO parse authorization response

		AuthenticationResponse authnResp = AuthenticationResponseParser.parse(new URI(req.getRequestURI()));

		if (authnResp instanceof AuthenticationErrorResponse) {

			String opAuthEndp = opMetaData.getAuthorizationEndpointURI().toString();
			String user = (String) stepCtx.get(RPContextConstants.CURRENT_USER_USERNAME);
			String pass = (String) stepCtx.get(RPContextConstants.CURRENT_USER_USERNAME);
			logger.log(String.format("Authentication at %s as %s with password %s failed:", opAuthEndp, user, pass));
			logger.logHttpRequest(req, req.getQueryString());
			// store auth failed hint in context
//			stepCtx.put(RPContextConstants.)
			// TODO: shoudl we store the authnResp in context, e.g. for later analysis
			return;
		}

		// in code flow, fetch token/idToken
		//


		// call UserInfo Endpoint?
		// should be a method in AbstractRP that is overwritten in subclasses


    }
}
