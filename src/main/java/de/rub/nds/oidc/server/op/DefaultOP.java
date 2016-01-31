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

import de.rub.nds.oidc.server.RequestPath;
import java.io.IOException;
import javax.json.Json;
import javax.json.JsonObject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author Tobias Wich
 */
public class DefaultOP extends AbstractOPImplementation {

	@Override
	public void webfinger(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String rel = req.getParameter("rel");
		String resource = req.getParameter("resource");
		String host = req.getParameter("host");
		if ("http://openid.net/specs/connect/1.0/issuer".equals(rel)) {
			JsonObject result = Json.createObjectBuilder()
					.add("subject", resource)
					.add("links", Json.createArrayBuilder().add(Json.createObjectBuilder()
					.add("rel", "http://openid.net/specs/connect/1.0/issuer")
					.add("href", path.getServerHost().toString())))
					.build();
			Json.createWriter(resp.getOutputStream()).writeObject(result);
			resp.setContentType("application/json; charset=UTF-8");
		} else {
			// return not handled
		}
	}

}
