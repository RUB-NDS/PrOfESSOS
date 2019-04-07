package de.rub.nds.oidc.learn;

import javax.inject.Inject;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

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
		removeEntryFromRegistry(se.getSession());
	}

	private void removeEntryFromRegistry(HttpSession session) {
		String testId = (String) session.getAttribute("testId");
		registry.deleteTestObject(testId);
	}
}
