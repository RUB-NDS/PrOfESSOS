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

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.UriBuilder;

/**
 *
 * @author Tobias Wich
 */
public class RequestPath {

	private final String ctxPath;
	private final String servletPath;
	private final String resourcePath;
	private final List<String> segments;
	private final URI originalRequestUri;
	private final String registrationEnforced;

	public RequestPath(HttpServletRequest req) {
		// resources look like this /<ctx>/<servlet>/<testId>/<resource>
		// or /<servlet>/<testId>/<resource>
		ctxPath = req.getContextPath();      // "" or "/foo"
		servletPath = req.getServletPath();  // "" or "/bar"
		String fullPath = req.getRequestURI();

		// seperate out the prefix
		// TODO: enforce-reg should be some static constant in OPIVconfig?
		String regexp = String.format("^%s%s(/enforce-rp-reg-.{8})?(/.*)$", Pattern.quote(ctxPath), Pattern.quote(servletPath));
		Pattern p = Pattern.compile(regexp);
		Matcher m = p.matcher(fullPath);
		m.matches();

		if (m.groupCount() == 2 ) {
			System.out.println("group count is 2");
			registrationEnforced = m.group(1);
			System.out.println("group 1: " + registrationEnforced);
			resourcePath = m.group(2);
			System.out.println("group 2: " + resourcePath);
		} else {
			System.out.println("group count is NOT  2");
			registrationEnforced = "";
			resourcePath = m.group(1);
		}
		// extract path segments
		List<String> segmentList = Arrays.stream(resourcePath.split("/"))
				.map(String::trim)
				.filter(e -> ! e.isEmpty())
				.collect(Collectors.toList());

		segments = Collections.unmodifiableList(segmentList);
		originalRequestUri = UriBuilder.fromUri(req.getRequestURL().toString()).build();
	}

	public List<String> getSegments() {
		return segments;
	}

	@Nonnull
	public String getTestId() {
		// assume the first element is the testId, if it is not, then there will be no match in the registry
		return segments.stream().findFirst().orElse("");
	}

	public String getFullResource() {
		return resourcePath;
	}

	public String getStrippedResource() {
		String result = getSegments().stream().skip(1)
				.reduce("", (current, next) -> current.concat("/" + next));
		return "".equals(result) ? "/" : result;
	}

	public URI getStrippedRequestUri() {
		URI result = UriBuilder.fromUri(originalRequestUri).replacePath(getStrippedResource()).build();
		return result;
	}

	public URI getServerHost() {
		return UriBuilder.fromUri(originalRequestUri)
				.replacePath(null)
				.replaceQuery(null)
				.build();
	}

	public URI getServerHostAndTestId() {
		return UriBuilder.fromUri(originalRequestUri)
				.replacePath(getTestId())
				.replaceQuery(null)
				.build();
	}

	public URI getDispatchUriAndTestId() {
		return UriBuilder.fromUri(originalRequestUri)
				.replacePath("/dispatch/" + getTestId())
				.replaceQuery(null)
				.build();
	}

	public String getRegistrationEnforced() {
		return registrationEnforced == null ? "" : registrationEnforced;
	}

}
