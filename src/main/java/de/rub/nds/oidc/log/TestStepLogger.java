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

import de.rub.nds.oidc.test_model.HttpRequestEntryType;
import de.rub.nds.oidc.test_model.HttpResponseEntryType;
import de.rub.nds.oidc.test_model.LogEntryType;
import de.rub.nds.oidc.test_model.ScreenshotEntryType;
import de.rub.nds.oidc.test_model.TestStepResultType;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import javax.annotation.Nonnull;
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

	public void log(byte[] screenshot, String mimeType) {
		LogEntryType e = createLogEntry();
		ScreenshotEntryType se = new ScreenshotEntryType();
		se.setData(screenshot);
		se.setMimeType(mimeType);
		e.setScreenshot(se);
		log(e);
	}

	public void logHttpRequest(HttpRequestEntryType req) {
		LogEntryType e = createLogEntry();
		e.setHttpRequest(req);
		log(e);
	}

	public void logHttpRequest(HttpResponseEntryType res) {
		LogEntryType e = createLogEntry();
		e.setHttpResponse(res);
		log(e);
	}

}
