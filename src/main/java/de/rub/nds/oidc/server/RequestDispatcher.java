/****************************************************************************
 * Copyright (C) 2016 Tobias Wich
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ***************************************************************************/

package de.rub.nds.oidc.server;

import de.rub.nds.oidc.server.op.OPInstance;
import de.rub.nds.oidc.server.rp.RPInstance;
import java.io.IOException;
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

		try {
			switch (serverName) {
				case OP1_HOST:
					handleOP(registry.getOP1(path.getTestId()));
					break;
				case OP2_HOST:
					handleOP(registry.getOP2(path.getTestId()));
					break;
				case RP_HOST:
					handleRP(registry.getRP(path.getTestId()));
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


	private void handleOP(ServerInstance<OPInstance> inst) {

	}

	private void handleRP(ServerInstance<RPInstance> inst) {

	}

}
