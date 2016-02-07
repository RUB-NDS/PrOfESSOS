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
import java.util.function.Supplier;
import javax.inject.Inject;
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
	private OPIVConfig opivCfg;

	@Inject
	public void setRegistry(TestInstanceRegistry registry) {
		this.registry = registry;
	}

	@Inject
	public void setOPIVConfig(OPIVConfig opivCfg) {
		this.opivCfg = opivCfg;
	}


	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String serverName = req.getScheme() + "://" + req.getHeader("Host");
		RequestPath path = new RequestPath(req);
		String testId = path.getTestId(); // may not be the

		try {
			if ((opivCfg.getOP1Scheme() + "://" + opivCfg.getOP1Host()).equals(serverName)) {
				handleOP(path, () -> registry.getOP1Supplier().apply(testId), req, resp);
			} else if ((opivCfg.getOP2Scheme() + "://" + opivCfg.getOP2Host()).equals(serverName)) {
				handleOP(path, () -> registry.getOP2Supplier().apply(testId), req, resp);
			} else if ((opivCfg.getRPScheme() + "://" + opivCfg.getRPHost()).equals(serverName)) {
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
		String testId = path.getTestId();
		String resource = path.getStrippedResource();
		ServerInstance<OPInstance> inst = instSupplier.get();

		if (inst == null) {
			String msg = String.format("OP instance for id %s is missing in the registry.", testId);
			throw new ServerInstanceMissingException(msg);
		} else {
			OPImplementation impl = inst.getInst().getImpl();
			impl.setBaseUri(path.getServerHostAndTestId());
			impl.setOPIVConfig(opivCfg);

			if (resource.startsWith(OPImplementation.WEBFINGER_PATH)) {
				impl.webfinger(path, req, resp);
			} else if (resource.startsWith(OPImplementation.PROVIDER_CONFIG_PATH)) {
				impl.providerConfiguration(path, req, resp);
			} else if (resource.startsWith(OPImplementation.JWKS_PATH)) {
				impl.jwks(path, req, resp);
			} else if (resource.startsWith(OPImplementation.REGISTER_CLIENT_PATH)) {
				impl.registerClient(path, req, resp);
			} else if (resource.startsWith(OPImplementation.AUTH_REQUEST_PATH)) {
				impl.authRequest(path, req, resp);
			} else if (resource.startsWith(OPImplementation.TOKEN_REQUEST_PATH)) {
				impl.tokenRequest(path, req, resp);
			} else if (resource.startsWith(OPImplementation.USER_INFO_REQUEST_PATH)) {
				impl.userInfoRequest(path, req, resp);
			} else {
				// TODO: match resources and call impl functions
				notFound(resource, resp);
			}
		}
	}

	private void handleRP(RequestPath path, Supplier<ServerInstance<RPInstance>> instSupplier, HttpServletRequest req,
			HttpServletResponse resp)
			throws ServerInstanceMissingException {
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

	private void notFound(String resource, HttpServletResponse res) throws IOException {
		String msg = "Resource '" + resource + "' is not available on the server.";
		res.sendError(HttpServletResponse.SC_NOT_FOUND, msg);
	}

}
