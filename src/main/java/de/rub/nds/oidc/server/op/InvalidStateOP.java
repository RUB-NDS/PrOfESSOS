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

import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author Tobias Wich
 */
public class InvalidStateOP extends DefaultOP {

	private State firstState;

	@Override
	protected State getState(AuthenticationRequest authReq) {
		if (params.getBool(OPParameterConstants.FORCE_STATE_INVALID_VALUE)) {
			return new State();
		} else if (params.getBool(OPParameterConstants.FORCE_STATE_OTHER_SESSION)) {
			if (firstState == null) {
				firstState = authReq.getState();
				// notify browser that the state is safed
				((CompletableFuture) stepCtx.get(OPContextConstants.RELOAD_BROWSER_FUTURE)).complete(null);
				// block execution, so that browser can start fresh
				try {
					CompletableFuture f = (CompletableFuture) stepCtx.get(OPContextConstants.BLOCK_OP_FUTURE);
					f.get(60, TimeUnit.SECONDS);
				} catch (ExecutionException | InterruptedException | TimeoutException ex) {
					// no problem ;-)
				}
				throw new RuntimeException("This call is never answered correctly.");
			} else {
				return firstState;
			}
		} else {
			return authReq.getState();
		}
	}

}
