/* 
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
 */

"use strict";


var OPIV = (function(module) {

	var testId;
	var testObject;
	var testRPConfig;
	var testReport;

	module.clear = function() {
		document.location.reload();
	};

	module.createRPTestPlan = function() {
		// request new test id
		$.post("learn/rp/create-test-object", initTestObject);
	};

	module.learnRP = function() {
		updateRPConfig();
		// call learning function
		$.post({
			url: "learn/rp/" + testId + "/learn",
			data: JSON.stringify(testRPConfig),
			contentType: "application/json",
			success: processLearnResponse
		});
	};

	module.testRPStep = function(stepId, stepContainer) {
		updateRPConfig();
		// call test function
		$.post({
			url: "learn/rp/" + testId + "/test/" + stepId,
			data: JSON.stringify(testRPConfig),
			contentType: "application/json",
			success: function(data) { processTestResponse(stepContainer, data); }
		});
	};

	function initTestObject(data) {
		testObject = data;
		testId = testObject.TestId;
		testRPConfig = testObject.TestRPConfig;
		testReport = testObject.TestReport;
		$("#test-id-display").html(document.createTextNode(testId));
		$("#op-id-display").html(document.createTextNode(testRPConfig.WebfingerResourceId));

		loadTestReport();
	}

	function loadTestReport() {
		var reportContainer = $("#test-report");
		reportContainer.html("");

		var testResults = testReport.TestStepResult;
		for (var i = 0; i < testResults.length; i++) {
			reportContainer.append(createTestStepResult(testResults[i]));
		}
	}

	function createTestStepResult(testResult) {
		var testDef = testResult.StepReference;

		var container = document.createElement("div");
		container.id = "step-result-" + testDef.Name;
		container.className = "step-container";

		// name and result image
		var head = document.createElement("div");
		container.appendChild(head);
		head.className = "step-head";
		head.innerHTML = "<b>" + testDef.Name + "</b>: ";
		head.appendChild(createResultImage(testResult.Result));
		// create test button
		var testForm = document.createElement("form");
		testForm.action = "javascript:;";
		testForm.onsubmit = function() { OPIV.testRPStep(testDef.Name, container); };
		var button = document.createElement("input");
		button.type = "submit";
		button.value = "Run Test";
		testForm.appendChild(button);
		head.appendChild(testForm);

		// description
		var descContainer = document.createElement("div");
		var desc = document.createElement("div");
		desc.style.visibility = "hidden";
		desc.className = "step-description";
		desc.innerHTML = testDef.Description;
		var hideImg = document.createElement("img");
		hideImg.src = "img/arrow-right.png";
		hideImg.width = 20;
		var hideImgLink = document.createElement("a");
		hideImgLink.href = "#";
		hideImgLink.innerHTML = "Description";
		hideImgLink.onclick = function() {
			if (desc.style.visibility === "hidden") {
				desc.style.visibility = "visible";
				hideImg.src = "img/arrow-down.png";
			} else {
				desc.style.visibility = "hidden";
				hideImg.src = "img/arrow-right.png";
			}
		};

		hideImgLink.appendChild(hideImg);
		descContainer.appendChild(hideImgLink);
		descContainer.appendChild(desc);
		container.appendChild(descContainer);

		var logContainer = document.createElement("div");
		logContainer.className = "step-log";
		container.appendChild(logContainer);

		return container;
	}

	function createResultImage(code) {
		var resultImg = document.createElement("img");
		resultImg.class = "status-image";
		resultImg.src = "img/" + code + ".png";
		resultImg.alt = code;
		resultImg.width = "20";
		resultImg.textContent = code;
		return resultImg;
	}

	function writeLog(logContainer, testLog) {
		// clear container first
		logContainer.innerHTML = "";

		for (var i = 0; i < testLog.length; i++) {
			var entry = testLog[i];
			var date = new Date(entry.Date);
			
			// create entries
			var entryContainer = document.createElement("div");
			logContainer.appendChild(entryContainer);

			var dateContainer = document.createElement("it");
			dateContainer.innerHTML = date.toString();
			entryContainer.appendChild(dateContainer);
			entryContainer.appendChild(document.createElement("br"));

			// process different kinds of
			if (entry.Text) {
				entryContainer.appendChild(createTextLogEntry(entry.Text));
			} else if (entry.Screenshot) {
				entryContainer.appendChild(createScreenshotLogEntry(entry.Screenshot));
			} else if (entry.HttpRequest) {
				entryContainer.appendChild(createHttpRequestLogEntry(entry.HttpRequest));
			} else if (entry.HttpResponse) {
				entryContainer.appendChild(createHttpResponseLogEntry(entry.HttpResponse));
			}
		}
	}

	function createTextLogEntry(text) {
		// TODO: check XSS attack vector
		var textContainer = document.createElement("div");
		//var textNode = document.createTextNode(text.replace(/\n/, "<br>"));
		//textContainer.appendChild(textNode);
		textContainer.innerHTML = text.replace(/\n/g, "<br>");
		return textContainer;
	}

	function createScreenshotLogEntry(screenshot) {
		var img = document.createElement("img");
		img.src = "data:" + screenshot.MimeType + ";base64," + screenshot.Data;
		return img;
	}

	function createHttpRequestLogEntry(req) {
		// TODO
	}

	function createHttpResponseLogEntry(res) {
		// TODO
	}

	function updateRPConfig() {
		 testRPConfig.UrlClientTarget = $("input[name='url-client-target']").val();
		 testRPConfig.InputFieldName = $("input[name='input-field-name']").val();
		 testRPConfig.SeleniumScript = $("textarea[name='selenium-script']").val();
	}

	function writeRPConfig(newTestRPConfig) {
		testRPConfig.UrlClientTarget = newTestRPConfig.UrlClientTarget;
		$("input[name='url-client-target']").val(testRPConfig.UrlClientTarget);
		testRPConfig.InputFieldName = newTestRPConfig.InputFieldName;
		$("input[name='input-field-name']").val(testRPConfig.InputFieldName);
		testRPConfig.SeleniumScript = newTestRPConfig.SeleniumScript;
		$("textarea[name='selenium-script']").val(testRPConfig.SeleniumScript);
	}

	function processLearnResponse(learnResult) {
		var stepResult = learnResult.TestStepResult;

		// update config
		writeRPConfig(learnResult.TestRPConfig);

		// update status
		var learnStatus = $("#learn-status");
		learnStatus.attr("alt", stepResult.Result);
		learnStatus.attr("src", "img/" + stepResult.Result + ".png");

		// write log
		writeLog(document.getElementById("learn-log"), stepResult.LogEntry);
	}

	function processTestResponse(stepContainer, learnResult) {
		var stepResult = learnResult.TestStepResult;

		// update status
		var statusImg = stepContainer.getElementsByClassName("status-image").item();
		statusImg.alt = stepResult.Result;
		statusImg.src = "img/" + stepResult.Result + ".png";

		// write log
		var logContainer = stepContainer.getElementsByClassName("step-log").item();
		writeLog(logContainer, stepResult.LogEntry);
	}

	return module;

})(OPIV || {});
