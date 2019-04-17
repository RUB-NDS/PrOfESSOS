package de.rub.nds.oidc.learn;

import javax.inject.Inject;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import java.util.ArrayList;

@WebListener
public class HttpSessionManager implements HttpSessionListener {

	private TestRunnerRegistry registry;

	@Inject
	public void setRegistry(TestRunnerRegistry registry) {
		this.registry = registry;
	}

	@Override
	public void sessionCreated(HttpSessionEvent se) {
		// ignore
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent se) {
		removeEntriesFromRegistry(se.getSession());
	}

	private void removeEntriesFromRegistry(HttpSession session) {
		@SuppressWarnings("unchecked")
		ArrayList<String> testIDs = (ArrayList) session.getAttribute("testIDs");
		testIDs.forEach(id -> registry.deleteTestObject(id));
	}
}
