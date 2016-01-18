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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;

/**
 *
 * @author Tobias Wich
 */
public class RequestPath {

	private final String ctxPath;
	private final String servletPath;
	private final String resourcePath;
	private final List<String> segments;

	public RequestPath(HttpServletRequest req) {
		// resources look like this /<ctx>/<servlet>/<testId>/<resource>
		// or /<ctx>/<servlet>/<testId>/<resource>
		ctxPath = req.getContextPath(); // "" or "/foo"
		servletPath = req.getServletPath();  // "" or "/bar"
		String fullPath = req.getRequestURI();

		// seperate out the prefix
		String regexp = String.format("^%s%s(/.+*)$", Pattern.quote(ctxPath), Pattern.quote(servletPath));
		Pattern p = Pattern.compile(regexp);
		Matcher m = p.matcher(fullPath);
		m.matches();
		resourcePath = m.group(1);

		// extract path segments
		List<String> segmentList = Arrays.stream(resourcePath.split("/"))
				.map(String::trim)
				.filter(e -> ! e.isEmpty())
				.collect(Collectors.toList());

		segments = Collections.unmodifiableList(segmentList);
	}

	public List<String> getSegments() {
		return segments;
	}

	public String getTestId() {
		// assume the first element is the testId, if it is not, then there will be no match in the registry
		return segments.stream().findFirst().orElse("");
	}

	public String getFullResource() {
		return resourcePath;
	}

	public String getStrippedResource() {
		return getSegments().stream().skip(1)
				.reduce("", (current, next) -> current.concat(next));
	}

}
