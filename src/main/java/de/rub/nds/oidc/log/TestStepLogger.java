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

import de.rub.nds.oidc.test_model.HeaderType;
import de.rub.nds.oidc.test_model.HttpRequestEntryType;
import de.rub.nds.oidc.test_model.HttpResponseEntryType;
import de.rub.nds.oidc.test_model.LogEntryType;
import de.rub.nds.oidc.test_model.ScreenshotEntryType;
import de.rub.nds.oidc.test_model.TestStepResultType;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
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

	private final TestStepResultType stepResult;
	protected final DatatypeFactory df;

	public TestStepLogger(TestStepResultType stepResult) {
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

	public void log(String text, Throwable ex) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);

		pw.println(text);
		ex.printStackTrace(pw);
		String fullText = sw.toString();

		log(fullText);
	}

	public void log(byte[] screenshot, String mimeType) {
		LogEntryType e = createLogEntry();
		ScreenshotEntryType se = new ScreenshotEntryType();
		se.setData(screenshot);
		se.setMimeType(mimeType);
		e.setScreenshot(se);
		log(e);
	}

	public void logHttpRequest(@Nonnull HttpServletRequest req, @Nullable String body) {
		HttpRequestEntryType entry = new HttpRequestEntryType();

		String reqLine = req.getMethod() + " " + req.getRequestURI();
		if (req.getQueryString() != null) {
			reqLine += "?" + req.getQueryString();
		}
		reqLine += " " + req.getProtocol();
		entry.setRequestLine(reqLine);

		entry.getHeader().addAll(readHeaders(Collections.list(req.getHeaderNames()), key -> {
			return Collections.list(req.getHeaders(key));
		}));

		entry.setBody(formatBody(req.getContentType(), body));

		logHttpRequest(entry);
	}

	public void logHttpRequest(@Nonnull HttpRequestEntryType req) {
		LogEntryType e = createLogEntry();
		e.setHttpRequest(req);
		log(e);
	}


	public void logHttpResponse(@Nonnull HttpServletResponse res, @Nullable String body) {
		HttpResponseEntryType entry = new HttpResponseEntryType();

		entry.setStatus(BigInteger.valueOf(res.getStatus()));
		entry.getHeader().addAll(readHeaders(res.getHeaderNames(), res::getHeaders));
		entry.setBody(formatBody(res.getContentType(), body));

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
					.map(value -> {
						HeaderType h = new HeaderType();
						h.setKey(key);
						h.setValue(value);
						return h;
					});
		}).collect(Collectors.toList());
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
