/****************************************************************************
 * Copyright 2016 Ruhr-Universität Bochum.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ***************************************************************************/

package de.rub.nds.oidc.server.op;

import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.openid.connect.sdk.rp.OIDCClientInformation;

/**
 *
 * @author Tobias Wich
 */
public class TRCOP extends DefaultOP {

	@Override
	protected String getTokenAudience(ClientID clientId) {
		if (params.getBool(OPParameterConstants.FORCE_TOKEN_HONEST_AUD)) {
			OIDCClientInformation info;
			info = (OIDCClientInformation) suiteCtx.get(OPContextConstants.REGISTERED_CLIENT_INFO_HONEST);
			ClientID honestId = info.getID();
			return honestId.getValue();
		} else {
			return super.getTokenAudience(clientId);
		}
	}

}
