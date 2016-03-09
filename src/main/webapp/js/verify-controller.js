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
	var learningComplete = false;

	module.clear = function() {
		document.location.reload();
	};

	module.createRPTestPlan = function() {
		// request new test id
		$.post("api/rp/create-test-object", initTestObject);
		
	};

	function isInitialized() {
		return typeof testId !== 'undefined';
	}

	module.loadDemo = function() {
		if (isInitialized()) {
			testRPConfig.UrlClientTarget = "http://www.honestsp.de:8080/simple-web-app/login";
			testRPConfig.InputFieldName = null;
			testRPConfig.SeleniumScript = null;
			testRPConfig.FinalValidUrl = "http://www.honestsp.de:8080/simple-web-app/";
			testRPConfig.HonestUserNeedle = "{sub=honest-op-test-subject, iss=" + testRPConfig.HonestWebfingerResourceId + "}";
			testRPConfig.EvilUserNeedle = "{sub=evil-op-test-subject, iss=" + testRPConfig.EvilWebfingerResourceId + "}";
			testRPConfig.ProfileUrl = "http://www.honestsp.de:8080/simple-web-app/user";

			writeRPConfigGUI(testRPConfig);
		}
	};

	module.runAllTests = function() {
		if (learningComplete) {
			// delete results first
			clearAllTests();

			// get containers and make iterator
			var containers = getTestContainers();
			var i = 0;
			var containerIt = function() {
				if (i < containers.length) {
					return containers[i++];
				} else {
					return null;
				}
			};

			// handler to perform iteration
			var completeHandler = function(xhr, status) {
				var nextContainer = containerIt();
				if (nextContainer) {
					OPIV.testRPStep(nextContainer.TestId, nextContainer.Container, completeHandler);
				}
			};

			// call for the first time
			var nextContainer = containerIt();
			if (nextContainer) {
				OPIV.testRPStep(nextContainer.TestId, nextContainer.Container, completeHandler);
			}
		} else {
			alert("Please make sure the learning phase was successful first.");
		}
	};

	function clearAllTests() {
		document.getElementById("test-report").innerHTML = "";
		loadTestReport();
	}

	function getTestContainers() {
		var result = [];
		
		// loop over all step definitions and find the matching container
		var testResults = testReport.TestStepResult;
		for (var i = 0; i < testResults.length; i++) {
			var nextResult = testResults[i];
			var testDef = nextResult.StepReference;
			var testId = testDef.Name;
			var container = document.getElementById("step-result-" + testDef.Name);
			result.push({ TestId: testId, Container: container });
		}

		return result;
	}

	module.learnRP = function(completeHandler) {
		// default parameters
		completeHandler = typeof completeHandler !== 'undefined' ? completeHandler : function() {};

		learningComplete = false;

		updateRPConfig();
		// call learning function
		$.post({
			url: "api/rp/" + testId + "/learn",
			data: JSON.stringify(testRPConfig),
			contentType: "application/json",
			success: processLearnResponse,
			error: learnResponseError,
			complete: completeHandler
		});
	};

	module.testRPStep = function(stepId, stepContainer, completeHandler) {
		// default parameters
		completeHandler = typeof completeHandler !== 'undefined' ? completeHandler : function() {};

		if (learningComplete) {
			updateRPConfig();
			// call test function
			$.post({
				url: "api/rp/" + testId + "/test/" + stepId,
				data: JSON.stringify(testRPConfig),
				contentType: "application/json",
				success: function(data) { processTestResponse(stepContainer, data); },
				error: function(xhr, status) { stepTestError(stepId, stepContainer, xhr, status); },
				complete: completeHandler
			});
		} else {
			alert("Please make sure the learning phase was successful first.");
		}
	};

	function initTestObject(data) {
		testObject = data;
		testId = testObject.TestId;
		testRPConfig = testObject.TestRPConfig;
		testReport = testObject.TestReport;
		$("#test-id-display").html(document.createTextNode(testId));
		$("#honest-op-id-display").html(document.createTextNode(testRPConfig.HonestWebfingerResourceId));
		$("#evil-op-id-display").html(document.createTextNode(testRPConfig.EvilWebfingerResourceId));

		// update config
		writeRPConfigGUI(testRPConfig);

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
		container.className = ("step-container " + testResult.Result);

		// name and result image
		var stepHead = document.createElement("div");
		container.appendChild(stepHead);
		stepHead.className = "step-head";
		var heading = document.createElement("h3");
		stepHead.appendChild(heading);
		heading.innerHTML = testDef.Name;
		var imageContainer = document.createElement("span");
		imageContainer.className = "status-image-container";
		heading.appendChild(imageContainer);
		createResultImage(imageContainer, testResult.Result);

		// create test button
		var testForm = document.createElement("form");
		testForm.action = "javascript:;";
		testForm.onsubmit = function() { OPIV.testRPStep(testDef.Name, container); };
		var button = document.createElement("button");
		button.className = "btn btn-default";
		button.type = "submit";
		button.value = "Run Test";
		button.innerHTML = "Run Test";
		testForm.appendChild(button);
		stepHead.appendChild(testForm);

		// description
		var descContainer = document.createElement("div");
		var desc = document.createElement("div");
		desc.style.display = "none";
		desc.className = "step-description";
		desc.innerHTML = getDescription(testDef);

		descContainer.appendChild(createHideImage(desc, "Description"));
		descContainer.appendChild(desc);
		container.appendChild(descContainer);

		// log
		var logContainer = document.createElement("div");
		var hideCaption = createHideImage(logContainer, "Test Log");
		logContainer.className = "step-log";
		container.appendChild(hideCaption);
		container.appendChild(logContainer);

		return container;
	}

	function getDescription(testDef) {
		var result = "";
		
		var concat = function(textArray) {
			var result = "";
			textArray.forEach(function(next) {
				if (typeof next === "string") {
					result += next;
				} else {
					console.log("Skipping entry of unknown type in description array.");
				}
			});
			return result;
		};

		// there seems to be countless possibilities how the description looks like according to the JAXB implementations
		if (typeof testDef.Description === "string") {
			result = testDef.Description;
		} else if (testDef.Description && Array.isArray(testDef.Description.value)) {
			result = concat(testDef.Description.value);
		} else if (testDef.Description && Array.isArray(testDef.Description.content)) {
			result = concat(testDef.Description.content);
		} else {
			console.log("Description of test '" + testDef.Name + "' does not contain an expected value.");
		}

		return result;
	}

	function createHideImage(containerToHide, hideText) {
		var hideImg = document.createElement("img");
		hideImg.src = "img/arrow-right.png";
		hideImg.width = 20;
		var hideImgLink = document.createElement("a");
		hideImgLink.appendChild(hideImg);
		hideImgLink.href = "javascript:;";
		hideImgLink.innerHTML = hideText;
		hideImgLink.onclick = function() {
			if (containerToHide.style.display === "none") {
				containerToHide.style.display = null;
				hideImg.src = "img/arrow-down.png";
			} else {
				containerToHide.style.display = "none";
				hideImg.src = "img/arrow-right.png";
			}
		};
		containerToHide.style.display = "none";

		return hideImgLink;
	}

	function createResultImage(container, code) {
		container.innerHTML = "";

		// create image element
		var resultImg = document.createElement("img");
		resultImg.className = "status-image";
		resultImg.src = "img/" + code + ".png";
		resultImg.alt = code;
		resultImg.textContent = code;
		container.appendChild(resultImg);

		// create text
		if (code === "FAIL") {
			var textContainer = document.createElement("span");
			textContainer.className = "status-image-description";
			textContainer.innerHTML = "Attack Successful";
			container.appendChild(textContainer);
		}
	}

	function writeLog(logContainer, testLog, hideLog) {
		// default parameters
		hideLog = typeof hideLog !== 'undefined' ? hideLog : false;

		// clear container first and set display status
		logContainer.innerHTML = "";
		logContainer.style.display = hideLog ? "none" : null;

		for (var i = 0; i < testLog.length; i++) {
			var entry = testLog[i];
			var date = new Date(entry.Date);

			// create entries
			var entryContainer = document.createElement("div");
			entryContainer.className = "log-entry-wrapper " + (i % 2 === 0 ? "even" : "odd");
			logContainer.appendChild(entryContainer);

			var dateContainer = document.createElement("em");
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
		textContainer.className = "log-entry";
		//var textNode = document.createTextNode(text.replace(/\n/, "<br>"));
		//textContainer.appendChild(textNode);
		textContainer.innerHTML = text.replace(/\n/g, "<br>");
		return textContainer;
	}

	function createScreenshotLogEntry(screenshot) {
		var container = document.createElement("div");
		container.className = "log-entry";
		
		var img = document.createElement("img");
		img.src = "data:" + screenshot.MimeType + ";base64," + screenshot.Data;
		
		container.appendChild(img);
		return container;
	}

	function createHttpRequestLogEntry(req) {
		var container = document.createElement("div");
		container.className = "log-entry";
		
		var result = document.createDocumentFragment();

		var type = document.createElement("strong");
		type.innerHTML = "HTTP Request";
		result.appendChild(type);

		var status = document.createElement("div");
		status.className = "log-entry";
		
		var method = req.RequestLine.substr(0,req.RequestLine.indexOf(" "));
		var url = req.RequestLine.substr(req.RequestLine.indexOf(" "));
		var methodDoc = document.createElement("mark");
		methodDoc.innerHTML = method;
		var urlDoc = document.createTextNode(url);
		
		//status.appendChild(document.createTextNode(req.RequestLine));
		status.appendChild(methodDoc);
		status.appendChild(urlDoc);
		
		result.appendChild(status);

		result.appendChild(createHttpHeaders(req.Header));
		result.appendChild(createHttpBody(req.Body));

		container.appendChild(result);
		return container;
	}

	function createHttpResponseLogEntry(res) {
		var container = document.createElement("div");
		container.className = "log-entry";
		
		var result = document.createDocumentFragment();

		var type = document.createElement("strong");
		type.innerHTML = "HTTP Response";
		result.appendChild(type);

		var status = document.createElement("div");
		status.className = "log-entry";
		
		var resStatus = document.createElement("mark");
		resStatus.innerHTML = res.Status;
		
		if (res.Status >= 400) {
			resStatus.className = "err";
		}
		
		//status.appendChild(document.createTextNode(res.Status));
		status.appendChild(resStatus);
		result.appendChild(status);

		result.appendChild(createHttpHeaders(res.Header));
		result.appendChild(createHttpBody(res.Body));

		container.appendChild(result);
		return container;
	}

	function createHttpHeaders(headers) {
		var container = document.createElement("div");

		var caption = document.createElement("b");
		caption.innerHTML = "Headers";
		container.appendChild(caption);

		var dl = document.createElement("dl");
		dl.className = "dl-horizontal";
		container.appendChild(createHideImage(dl, "->"));
		container.appendChild(dl);

		if (headers) {
			for (var i = 0; i < headers.length; i++) {
				var entry = headers[i];
				var dt = document.createElement("dt");
				dt.appendChild(document.createTextNode(entry.Key));
				dl.appendChild(dt);
				var dd = document.createElement("dd");
				dd.appendChild(document.createTextNode(entry.value));
				dl.appendChild(dd);
			}
		}

		return container;
	}

	function createHttpBody(body) {
		var container = document.createElement("div");

		var caption = document.createElement("b");
		caption.innerHTML = "Body";
		container.appendChild(caption);

		var content = document.createElement("pre");
		content.appendChild(document.createTextNode(body));
		container.appendChild(content);

		return container;
	}

	function updateRPConfig() {
		 testRPConfig.UrlClientTarget = $("input[name='url-client-target']").val();
		 testRPConfig.InputFieldName = $("input[name='input-field-name']").val();
		 testRPConfig.SeleniumScript = $("textarea[name='selenium-script']").val();
		 testRPConfig.FinalValidUrl = $("input[name='url-client-target-success']").val();
		 testRPConfig.HonestUserNeedle = $("input[name='honest-user-needle']").val();
		 testRPConfig.EvilUserNeedle = $("input[name='evil-user-needle']").val();
		 testRPConfig.ProfileUrl = $("input[name='user-profile-url']").val();
	}

	function writeRPConfig(newTestRPConfig) {
		testRPConfig.UrlClientTarget = newTestRPConfig.UrlClientTarget;
		testRPConfig.InputFieldName = newTestRPConfig.InputFieldName;
		testRPConfig.SeleniumScript = newTestRPConfig.SeleniumScript;
		testRPConfig.FinalValidUrl = newTestRPConfig.FinalValidUrl;
		testRPConfig.HonestUserNeedle = newTestRPConfig.HonestUserNeedle;
		testRPConfig.EvilUserNeedle = newTestRPConfig.EvilUserNeedle;
		testRPConfig.ProfileUrl = newTestRPConfig.ProfileUrl;

		writeRPConfigGUI(newTestRPConfig);
	}

	function writeRPConfigGUI(newTestRPConfig) {
		$("input[name='url-client-target']").val(newTestRPConfig.UrlClientTarget);
		$("input[name='input-field-name']").val(newTestRPConfig.InputFieldName);
		$("textarea[name='selenium-script']").val(newTestRPConfig.SeleniumScript);
		$("input[name='url-client-target-success']").val(newTestRPConfig.FinalValidUrl);
		$("input[name='honest-user-needle']").val(newTestRPConfig.HonestUserNeedle);
		$("input[name='evil-user-needle']").val(newTestRPConfig.EvilUserNeedle);
		$("input[name='user-profile-url']").val(newTestRPConfig.ProfileUrl);
	}

	function processLearnResponse(learnResult) {
		var stepResult = learnResult.TestStepResult;
		var testPassed = stepResult.Result === "PASS";

		// update config
		if (learnResult.TestRPConfig) {
			writeRPConfig(learnResult.TestRPConfig);
		}

		// update status
		var learnStatus = $("#learn-status");
		learnStatus.attr("alt", stepResult.Result);
		learnStatus.attr("src", "img/" + stepResult.Result + ".png");

		// write log
		var learnLog = document.getElementById("learn-log");
		writeLog(learnLog, stepResult.LogEntry, testPassed);

		// grant access to tests if OK is returned
		if (testPassed) {
			learningComplete = true;
		}
	}


	function createHttpErrorStepResult(xhr, status) {
		return {
			TestStepResult: {
				Result: "UNDETERMINED",
				LogEntry: [
					{ Date: new Date().toISOString(),
					  Text: "Request failed with status '" + status + "' and code " + xhr.status + "." }
				]
			}
		};
	}

	function learnResponseError(xhr, status) {
		var result = createHttpErrorStepResult(xhr, status);
		processLearnResponse(result);
	}

	function processTestResponse(stepContainer, learnResult) {
		var stepResult = learnResult.TestStepResult;
		var testPassed = stepResult.Result === "PASS";

		// update status
		var statusImgContainer = stepContainer.getElementsByClassName("status-image-container")[0];
		createResultImage(statusImgContainer, stepResult.Result);

		// write log
		var logContainer = stepContainer.getElementsByClassName("step-log")[0];
		writeLog(logContainer, stepResult.LogEntry, testPassed);
	}

	function stepTestError(stepId, stepContainer, xhr, status) {
		var result = createHttpErrorStepResult(xhr, status);
		processTestResponse(stepContainer, result);
	}

	return module;

})(OPIV || {});
