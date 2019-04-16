package de.rub.nds.oidc.server.rp;

import com.nimbusds.oauth2.sdk.GrantType;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod;
import com.nimbusds.openid.connect.sdk.OIDCResponseTypeValue;
import com.nimbusds.openid.connect.sdk.rp.OIDCClientInformation;
import de.rub.nds.oidc.utils.InstanceParameters;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static de.rub.nds.oidc.server.rp.RPParameterConstants.*;

public class RPConfigHelper {

	// prevent public instantiation
	private RPConfigHelper() {
	}

	/**
	 * Compares the provided client configuration metadata with the
	 * TestStep parameters that directly affect a client's registration
	 * and configuration
	 *
	 * @param ci      The ClientInformation to check against
	 * @param params  The RPInstanceParameters, initialized with the TestStepReference parameters
	 * @param stepCtx optional additional parameters
	 * @return true, if TestStep parameters are compatible with client config (TestStep can be run),
	 * false, if client configuration does not allow to run this TestStep
	 */
	public static boolean testRunnableForConfig(@Nonnull OIDCClientInformation ci, @Nonnull InstanceParameters params, @Nullable Map<String, Object> stepCtx) {
		boolean grantResult;
		if (params.getBool(REGISTER_GRANT_TYPE_IMPLICIT)) {
			grantResult = ci.getOIDCMetadata().getGrantTypes() != null && ci.getOIDCMetadata().getGrantTypes().contains(GrantType.IMPLICIT);
		} else if (params.getBool(REGISTER_GRANT_TYPE_REFRESH)) {
			grantResult = ci.getOIDCMetadata().getGrantTypes() != null && ci.getOIDCMetadata().getGrantTypes().contains(GrantType.REFRESH_TOKEN);
		} else {
			// default grant
//			grantResult = ci.getOIDCMetadata().getGrantTypes() != null && ci.getOIDCMetadata().getGrantTypes().contains(GrantType.AUTHORIZATION_CODE);
			grantResult = true;
		}


		boolean rtResult = true;
		Set<ResponseType> rts = ci.getOIDCMetadata().getResponseTypes();
		if (rts == null) {
			// assume code as default
			rts = new HashSet<>();
			rts.add(new ResponseType(ResponseType.Value.CODE));
		}

		if (params.getBool(REGISTER_RESPONSETYPE_CODE)) {
			rtResult &= rts.contains(new ResponseType(ResponseType.Value.CODE));
		}
		if (params.getBool(REGISTER_RESPONSETYPE_TOKEN)) {
			rtResult &= rts.contains(new ResponseType(ResponseType.Value.TOKEN));
		}
		if (params.getBool(REGISTER_RESPONSETYPE_IDTOKEN)) {
			rtResult &= rts.contains(new ResponseType(OIDCResponseTypeValue.ID_TOKEN));
		}
		if (params.getBool(REGISTER_RESPONSETYPE_TOKEN_IDTOKEN)) {
			rtResult &= rts.contains(new ResponseType(OIDCResponseTypeValue.ID_TOKEN, ResponseType.Value.TOKEN));
		}
		if (params.getBool(REGISTER_RESPONSETYPE_CODE_IDTOKEN)) {
			rtResult &= rts.contains(new ResponseType(OIDCResponseTypeValue.ID_TOKEN, ResponseType.Value.CODE));
		}
		if (params.getBool(REGISTER_RESPONSETYPE_CODE_TOKEN)) {
			rtResult &= rts.contains(new ResponseType(ResponseType.Value.CODE, ResponseType.Value.TOKEN));
		}
		if (params.getBool(REGISTER_RESPONSETYPE_CODE_TOKEN_IDTOKEN)) {
			rtResult &= rts.contains(new ResponseType(OIDCResponseTypeValue.ID_TOKEN, ResponseType.Value.TOKEN, ResponseType.Value.CODE));
		}


		boolean authResult;
		ClientAuthenticationMethod method = ci.getOIDCMetadata().getTokenEndpointAuthMethod();
		if (method == null) {
			// default method
			method = ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
		}

		if (params.getBool(REGISTER_CLIENTAUTH_POST)) {
			authResult = method.equals(ClientAuthenticationMethod.CLIENT_SECRET_POST);
		} else if (params.getBool(REGISTER_CLIENTAUTH_NONE)) {
			authResult = method.equals(ClientAuthenticationMethod.NONE);
		} else if (params.getBool(REGISTER_CLIENTAUTH_JWT)) {
			authResult = method.equals(ClientAuthenticationMethod.CLIENT_SECRET_JWT);
		} else if (params.getBool(REGISTER_CLIENTAUTH_PK_JWT)) {
			authResult = method.equals(ClientAuthenticationMethod.PRIVATE_KEY_JWT);
		} else {
			// client_secret_basic
			authResult = method.equals(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
		}

		boolean result = grantResult && authResult && rtResult;
		return result;
	}
}
