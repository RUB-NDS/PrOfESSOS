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

	private final String OP1_HOST = "op1.example.com";
	private final String OP2_HOST = "op2.example.com";
	private final String RP_HOST = "rp.example.com";

	private TestInstanceRegistry registry;

	@Inject
	public void setRegistry(TestInstanceRegistry registry) {
		this.registry = registry;
	}


	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String serverName = req.getServerName();
		RequestPath path = new RequestPath(req);
		String testId = path.getTestId(); // may not be the

		try {
			switch (serverName) {
				case OP1_HOST:
					handleOP(path, () -> registry.getOP1Supplier().apply(testId));
					break;
				case OP2_HOST:
					handleOP(path, () -> registry.getOP2Supplier().apply(testId));
					break;
				case RP_HOST:
					handleRP(path, () -> registry.getRPSupplier().apply(testId));
					break;
				default:
					String msg = "Servername (" + serverName + ") is not handled by the dispatcher.";
					throw new UnknownEndpointException(msg);
			}
		} catch (ServerInstanceMissingException ex) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
		} catch (UnknownEndpointException ex) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
		}
	}


	private void handleOP(RequestPath path, Supplier<ServerInstance<OPInstance>> instSupplier)
			throws ServerInstanceMissingException {
		String fullResource = path.getFullResource();

		// process known resources
		if (fullResource.startsWith(".well-known/webfinger")) {
			// TODO: implement webfinger and other common logic
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
					// TODO: match resources and call impl functions
					default:

				}
			}
		}
	}

	private void handleRP(RequestPath path, Supplier<ServerInstance<RPInstance>> instSupplier)
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

}
