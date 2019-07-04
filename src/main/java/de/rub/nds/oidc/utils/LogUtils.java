/****************************************************************************
 * Copyright 2019 Ruhr-Universität Bochum.
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

package de.rub.nds.oidc.utils;

import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import de.rub.nds.oidc.server.op.OPType;
import de.rub.nds.oidc.server.rp.RPType;
import javax.servlet.http.HttpServletResponse;


/**
 *
 * @author Tobias Wich
 */
public class LogUtils {

	public static void addSenderHeader(HttpServletResponse resp, OPType type) {
		resp.addHeader("X-Prof-Sender", type + "-OP");
	}

	public static void addSenderHeader(HttpServletResponse resp, RPType type) {
		resp.addHeader("X-Prof-Sender", type + "-RP");
	}

	public static void addSenderHeader(HTTPRequest regHttpRequest, RPType type) {
		regHttpRequest.getHeaders().put("X-Prof-Sender", type + "-RP");
	}

}
