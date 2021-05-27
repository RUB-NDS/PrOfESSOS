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

package de.rub.nds.oidc.log;

import com.google.common.base.Strings;
import com.nimbusds.common.contenttype.ContentType;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import de.rub.nds.oidc.test_model.*;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonStructure;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

/**
 * Logger instance bound to a certain test step.
 *
 * @author Tobias Wich
 */
public class TestStepLogger {

	private final String testIdPath;
	private final TestStepResultType stepResult;
	protected final DatatypeFactory df;

	public TestStepLogger(TestStepResultType stepResult, String testIdPath) {
		this.testIdPath = testIdPath;
		try {
			this.stepResult = stepResult;
			this.df = DatatypeFactory.newInstance();
		} catch (DatatypeConfigurationException ex) {
			throw new RuntimeException("JAXB datatype conversion not available.", ex);
		}
	}

	protected LogEntryType createLogEntry() {
		LogEntryType e = new LogEntryType();
		GregorianCalendar date = new GregorianCalendar();
		date.setTime(new Date());
		date.setTimeZone(TimeZone.getTimeZone("UTC"));
		XMLGregorianCalendar cal = df.newXMLGregorianCalendar(date);
		e.setDate(cal);
		return e;
	}

	protected synchronized void log(@Nonnull LogEntryType entry) {
		stepResult.getLogEntry().add(entry);
	}

	public void log(String text) {
		LogEntryType e = createLogEntry();
		e.setText(text);
		log(e);
	}

	public void log(Throwable ex) {
		log(null, ex);
	}
	
	public void log(@Nullable String text, Throwable ex) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);

		ex.printStackTrace(pw);
		String fullText = sw.toString();

		logCodeBlock(text, fullText);
	}

	public void log(byte[] screenshot, String mimeType) {
		LogEntryType e = createLogEntry();
		ScreenshotEntryType se = new ScreenshotEntryType();
		se.setData(screenshot);
		se.setMimeType(mimeType);
		e.setScreenshot(se);
		log(e);
	}

	public void logCodeBlock(String content) {
		logCodeBlock(null, content);
	}

	public void logCodeBlock(@Nullable String description, @Nonnull String content) {
		CodeBlockEntryType entry = new CodeBlockEntryType();
		if (!Strings.isNullOrEmpty(description)) {
			entry.setDescription(description);
		}
		entry.setContent(content);
		
		LogEntryType e = createLogEntry();
		e.setCodeBlock(entry);
		log(e);
	}

	private String removeDispatchPrefix(String reqUri) {
		String prefix = "/dispatch" + testIdPath;
		if (reqUri.startsWith(prefix)) {
			reqUri = reqUri.substring(prefix.length());
			// check that path starts with a /
			if (! reqUri.startsWith("/")) {
				reqUri = "/" + reqUri;
			}
		}

		return reqUri;
	}

	public void logHttpRequest(@Nonnull HttpServletRequest req, @Nullable String body) {
		HttpRequestEntryType entry = new HttpRequestEntryType();

		String reqLine = req.getMethod() + " " + removeDispatchPrefix(req.getRequestURI());
		if (req.getQueryString() != null) {
			reqLine += "?" + req.getQueryString();
		}
		reqLine += " " + req.getProtocol();
		entry.setRequestLine(reqLine);

		// add special headers to indicate the protocol scheme and port
		entry.getHeader().add(createHeader("X-Protocol-Scheme", req.getScheme()));
		entry.getHeader().add(createHeader("X-Protocol-Port", Integer.toString(req.getServerPort())));

		entry.getHeader().addAll(readHeaders(Collections.list(req.getHeaderNames()), key -> {
			return Collections.list(req.getHeaders(key));
		}));

		entry.setBody(formatBody(req.getContentType(), body));

		logHttpRequest(entry);
	}

	public void logHttpRequest(@Nonnull HTTPRequest req, @Nullable String body ){
				HttpRequestEntryType entry = new HttpRequestEntryType();

		String reqLine = req.getMethod() + " " + removeDispatchPrefix(req.getURL().getPath());
		// nimbus SDK HttpRequest getQuery() returns the body for POST or the QueryString for GET, see:
		// https://static.javadoc.io/com.nimbusds/oauth2-oidc-sdk/5.8/com/nimbusds/oauth2/sdk/http/HTTPRequest.html#getQuery--
		if (req.getQuery() != null) {
			if (req.getMethod() != HTTPRequest.Method.POST) {
				reqLine += "?" + req.getQuery();
			} else {
				entry.setBody(formatBody(req.getHeaderValue("Content-Type").toString(), req.getQuery()));
			}
		}
//		reqLine += " " + req.getProtocol();
		entry.setRequestLine(reqLine);

		// add special headers to indicate the protocol scheme and port
		entry.getHeader().add(createHeader("X-Protocol-Scheme", req.getURL().getProtocol()));
		entry.getHeader().add(createHeader("X-Protocol-Port", Integer.toString(req.getURL().getPort())));

		entry.getHeader().addAll(readHeaders(req.getHeaderMap()));

		logHttpRequest(entry);
	}

	public void logHttpRequest(@Nonnull HttpRequestEntryType req) {
		LogEntryType e = createLogEntry();
		e.setHttpRequest(req);
		log(e);
	}

	public void logHttpResponse(@Nonnull HttpServletResponse res, @Nullable String body) {
		logHttpResponse(res, body, true);
	}

	public void logHttpResponse(@Nonnull HttpServletResponse res, @Nullable String body, @Nullable boolean formatBody) {
		HttpResponseEntryType entry = new HttpResponseEntryType();

		entry.setStatus(BigInteger.valueOf(res.getStatus()));
		entry.getHeader().addAll(readHeaders(res.getHeaderNames(), res::getHeaders));

		String content = body;
		if (formatBody) {
			content = formatBody(res.getContentType(), body);
		}
		entry.setBody(content);

		logHttpResponse(entry);
	}

	// nimbus SDK uses different response type
	public void logHttpResponse(@Nonnull HTTPResponse res, @Nullable String body) {
		HttpResponseEntryType entry = new HttpResponseEntryType();

		entry.setStatus(BigInteger.valueOf(res.getStatusCode()));
		entry.getHeader().addAll(readHeaders(res.getHeaderMap()));
		entry.setBody(formatBody(res.getHeaderValue("Content-Type").toString(), body));

		logHttpResponse(entry);
	}

	public void logHttpResponse(HttpResponseEntryType res) {
		LogEntryType e = createLogEntry();
		e.setHttpResponse(res);
		log(e);
	}


	private List<HeaderType> readHeaders(Collection<String> names, Function<String, Collection<String>> headers) {
		return names.stream().flatMap(key -> {
			// process list of entries matching this key
			return headers.apply(key).stream()
					.map(value -> createHeader(key, value));
		}).collect(Collectors.toList());
	}

	// nimbus SDK uses different types
	private List<HeaderType> readHeaders(Map<String, List<String>> headers) {
		List<HeaderType> result = new ArrayList<>();
//		for (String key : headers.keySet()) {
//			result.add(createHeader(key, headers.get(key)));
//		}
		for (Map.Entry<String,List<String>> e : headers.entrySet()) {
			result.add(createHeader(e.getKey(), e.getValue().get(0)));
		}
		return result;
	}

	private HeaderType createHeader(String key, String value) {
		HeaderType h = new HeaderType();
		h.setKey(key);
		h.setValue(value);
		return h;
	}

	private String formatBody(String contentType, String body) {
		if (body != null && contentType != null) {
			if (contentType.startsWith("application/json")) {
				try {
					JsonStructure json = Json.createReader(new StringReader(body)).read();
					StringWriter w = new StringWriter();
					HashMap<String, Object> p = new HashMap<>();
					p.put(JsonGenerator.PRETTY_PRINTING, true);
					Json.createWriterFactory(p).createWriter(w).write(json);
					return w.toString();
				} catch (JsonException ex) {
					return body;
				}
			} else {
				return body;
			}
		} else {
			return null;
		}
	}

}
