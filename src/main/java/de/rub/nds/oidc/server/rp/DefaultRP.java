package de.rub.nds.oidc.server.rp;

import de.rub.nds.oidc.server.RequestPath;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class DefaultRP extends AbstractRPImplementation {

    @Override
    public void callback(RequestPath path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // TODO parse authorization response
    }
}
