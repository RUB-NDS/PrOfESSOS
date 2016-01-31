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

package de.rub.nds.oidc.server;

import de.rub.nds.oidc.server.op.OPImplementation;
import de.rub.nds.oidc.server.op.OPInstance;
import de.rub.nds.oidc.server.rp.RPInstance;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author Tobias Wich
 */
@WebServlet(name = "dispatcher", urlPatterns = {
	"/dispatch/*"
})
public class RequestDispatcher extends HttpServlet {

	private TestInstanceRegistry registry;
	private EndpointHosts hosts;

	@Inject
	public void setRegistry(TestInstanceRegistry registry) {
		this.registry = registry;
	}

	@Inject
	public void setHosts(EndpointHosts hosts) {
		this.hosts = hosts;
	}


	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String serverName = req.getServerName();
		RequestPath path = new RequestPath(req);
		String testId = path.getTestId(); // may not be the

		try {
			if (hosts.getOP1Host().equals(serverName)) {
				handleOP(path, () -> registry.getOP1Supplier().apply(testId), req, resp);
			} else if (hosts.getOP2Host().equals(serverName)) {
				handleOP(path, () -> registry.getOP2Supplier().apply(testId), req, resp);
			} else if (hosts.getRPHost().equals(serverName)) {
				handleRP(path, () -> registry.getRPSupplier().apply(testId), req, resp);
			} else {
				String msg = "Servername (" + serverName + ") is not handled by the dispatcher.";
				throw new UnknownEndpointException(msg);
			}
		} catch (ServerInstanceMissingException ex) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
		} catch (UnknownEndpointException ex) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
		}
	}


	private void handleOP(RequestPath path, Supplier<ServerInstance<OPInstance>> instSupplier, HttpServletRequest req,
			HttpServletResponse resp)
			throws ServerInstanceMissingException, IOException {
		String fullResource = path.getFullResource();

		// process known resources
		if (fullResource.startsWith("/.well-known/webfinger")) {
			webfingerOP(req, resp);
		} else {
			// let the dispatcher handle everything else
			String testId = path.getTestId();
			String resource = path.getStrippedResource();
			ServerInstance<OPInstance> inst = instSupplier.get();

			if (inst == null) {
				String msg = String.format("OP instance for id %s is missing in the registry.", testId);
				throw new ServerInstanceMissingException(msg);
			} else {
				OPImplementation impl = inst.getInst().getImpl();
				switch (resource) {
					case "/.well-known/webfinger":
						impl.webfinger(path, req, resp);
					// TODO: match resources and call impl functions
					default:
						notFound(resource, resp);
				}
			}
		}
	}

	private void handleRP(RequestPath path, Supplier<ServerInstance<RPInstance>> instSupplier, HttpServletRequest req,
			HttpServletResponse resp)
			throws ServerInstanceMissingException {
		String fullResource = path.getFullResource();

		// process known resources
		if (fullResource.startsWith(".well-known/webfinger")) {
			// TODO: implement webfinger and other common logic

		} else {
			// let the dispatcher handle everything else
			String testId = path.getTestId();
			String resource = path.getStrippedResource();
			ServerInstance<RPInstance> inst = instSupplier.get();

			if (inst == null) {
				String msg = String.format("RP instance for id %s is missing in the registry.", testId);
				throw new ServerInstanceMissingException(msg);
			} else {

			}
		}
	}

	private void notFound(String resource, HttpServletResponse res) throws IOException {
		String msg = "Resource '" + resource + "' is not available on the server.";
		res.sendError(HttpServletResponse.SC_NOT_FOUND, msg);
	}

	private void webfingerOP(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		try {
			String rel = req.getParameter("rel");
			String resource = req.getParameter("resource");
			String host = req.getHeader("Host");

			// extract testId from resource
			String testId = new URI(resource).getPath();

			if ("http://openid.net/specs/connect/1.0/issuer".equals(rel)) {
				JsonObject result = Json.createObjectBuilder()
						.add("subject", resource)
						.add("links", Json.createArrayBuilder().add(Json.createObjectBuilder()
								.add("rel", "http://openid.net/specs/connect/1.0/issuer")
								.add("href", "https://" + host + testId)))
						.build();
				Json.createWriter(resp.getOutputStream()).writeObject(result);
				resp.setContentType("application/json; charset=UTF-8");
			} else {
				// return not handled
				resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown webfinger request.");
			}
		} catch (URISyntaxException ex) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Only URIs containing the testId allowed as resource.");
		}
	}

}
