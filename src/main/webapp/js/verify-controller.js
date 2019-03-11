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
	var testConfig;
	var testConfigType;
	var testReport;
	var learningComplete = false;

	const RP_CONFIG_TYPE = "de.rub.nds.oidc.test_model.TestRPConfigType";
	const OP_CONFIG_TYPE = "de.rub.nds.oidc.test_model.TestOPConfigType";

	module.clear = function() {
		document.location.reload();
	};

	module.createTestPlan = function(testPlanType) {
		// request new test id
		if (testPlanType === "RP-TestPlan") {
			$.post("api/rp/create-test-object", initTestObject);
		} else if ((testPlanType === "OP-TestPlan")) {
			$.post("api/op/create-test-object", initTestObject);
		}
	};

	module.cancelProcess = function() {
		// TODO: cancel running XHR
		hideWaitDialog();
	};

	function isInitialized() {
		return typeof testId !== 'undefined';
	}

	module.loadDemo = function() {
		if (isInitialized()) {
			if (testConfig["Type"] === RP_CONFIG_TYPE) {
				testConfig.UrlClientTarget = "http://www.honestsp.de:8080/simple-web-app/login";
				testConfig.InputFieldName = null;
				testConfig.SeleniumScript = null;
				testConfig.FinalValidUrl = "http://www.honestsp.de:8080/simple-web-app/";
				testConfig.HonestUserNeedle = "{sub=honest-op-test-subject, iss=" + testConfig.HonestWebfingerResourceId + "}";
				testConfig.EvilUserNeedle = "{sub=evil-op-test-subject, iss=" + testConfig.EvilWebfingerResourceId + "}";
				testConfig.ProfileUrl = "http://www.honestsp.de:8080/simple-web-app/user";

				writeRPConfigGUI(testConfig);
			}
			if (testConfig["Type"] === OP_CONFIG_TYPE) {
				testConfig.UrlOPTarget = "http://honestidp.de:8080/openid-connect-server-webapp";
				testConfig.User1Name = "user1";
				testConfig.User1Pass = "user1pass";
				testConfig.User2Name = "user2";
				testConfig.User2Pass = "user2pass";
				testConfig.OPMetadata = "";
				testConfig.Client1Config = "";
				testConfig.Client2Config = "";
				testConfig.AccessToken1 = "";
				testConfig.AccessToken2 = "";
				testConfig.LoginScript = "";
				testConfig.ConsentScript = "";

				writeOPConfigGUI(testConfig);
				$("#collapse1").collapse('show');
				$("#collapse2").collapse('hide');
			}
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
					OPIV.testStep(nextContainer.TestId, nextContainer.Container, completeHandler);
				} else {
					hideWaitDialog();
				}
			};

			// call for the first time
			var nextContainer = containerIt();
			if (nextContainer) {
				OPIV.testStep(nextContainer.TestId, nextContainer.Container, completeHandler);
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
		// min check based on form's "required" attribute
		let activeForm = getActiveRPConfigForm();
		if (markFormErrors(activeForm)) {
			return false;
		}
		removeFormErrorMark(activeForm);

		updateRPConfig();

		let url = "api/rp/" + testId + "/learn";
		learn(completeHandler, url);
	};

	module.learnOP = function(completeHandler) {
		// min check based on form's "required" attribute
		let activeForm = getActiveOPConfigForm();
		if (markFormErrors(activeForm)) {
			return false;
		}
		removeFormErrorMark(activeForm);

		updateOPConfig();
		let url = "api/op/" + testId + "/learn";
		learn(completeHandler, url);
	};

	module.submitRPLearningForm = function() {
		let activeForm = getActiveRPConfigForm();
		activeForm.submit();
	}

	function getActiveRPConfigForm() {
		return $("#form-config-tab").hasClass("active") ? $("#rp-learn-form") : $("#rp-learn-json-form");
	}

	module.submitOPLearningForm = function() {
		let activeForm = getActiveOPConfigForm();
		activeForm.submit();
	}

	function getActiveOPConfigForm() {
		return $("#form-config-tab").hasClass("active") ? $("#op-learn-form") : $("#op-learn-json-form");
	}

	function markFormErrors(form) {
		let hasError = false;
		$(form).find("[required]").each(function(){
			if ( !$(this).val() ) {
				hasError = true;
				$(this).parent().addClass("has-error");
			}
		});
		if (testConfigType === OP_CONFIG_TYPE && $("#form-config-tab").hasClass("active")) {
			if (!$("#url-op-target").val() && !$("#op-metadata-json").val()) {
				$("#url-op-target").parent().addClass("has-error");
				hasError = true;
			}
		}

		return hasError;
	}

	function removeFormErrorMark() {
		$("#rp-learn-form").find(".has-error").each(function(){
			$(this).removeClass("has-error");
		});
		$("#rp-learn-json-form").find(".has-error").each(function(){
			$(this).removeClass("has-error");
		});
	}

	function learn(completeHandler, url) {
		showWaitDialog();
		// default parameters
		completeHandler = typeof completeHandler !== 'undefined' ? completeHandler : function() { hideWaitDialog(); };
		learningComplete = false;

		// call learning function
		$.post({
			url: url,
			data: JSON.stringify(testConfig),
			contentType: "application/json",
			success: processLearnResponse,
			error: learnResponseError,
			complete: completeHandler
		});
	};

	module.testStep = function(stepId, stepContainer, completeHandler) {
		// default parameters
		completeHandler = typeof completeHandler !== 'undefined' ? completeHandler : function() { hideWaitDialog(); };

		if (learningComplete) {
			showWaitDialog();
			let apiPath = testConfig["Type"] === RP_CONFIG_TYPE ? "api/rp/" : "api/op/";

			// call test function
			$.post({
				url: apiPath + testId + "/test/" + stepId,
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
		testConfig = testObject.TestConfig;
		testReport = testObject.TestReport;
		testConfigType = testConfig.Type;

		$("#test-id-display").html(document.createTextNode(testId));

		if (testConfigType === RP_CONFIG_TYPE) {
			// configuration for rp-verifier
			$("#honest-op-id-display").html(document.createTextNode(testConfig.HonestWebfingerResourceId));
			$("#evil-op-id-display").html(document.createTextNode(testConfig.EvilWebfingerResourceId));
		}
		if (testConfigType === OP_CONFIG_TYPE) {
			// configuration for op-verifier
			$("#honest-rp-id-display").html(document.createTextNode(testConfig.HonestRpResourceId));
			$("#evil-rp-id-display").html(document.createTextNode(testConfig.EvilRpResourceId));
		}

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
		// TODO: RP verifier specific ???
		testForm.onsubmit = function() { OPIV.testStep(testDef.Name, container); };
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
		textContainer.style.overflow = "auto";
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
		var host = findHeader("host", req.Header);
		var methodDoc = document.createElement("mark");
		methodDoc.innerHTML = method;
		if (host) {
			// TODO: why do all requests miss the Host header???
			var urlDoc = document.createTextNode("[" + host + "] " + url);
		} else {
			var urlDoc = document.createTextNode(" " + url);
		}

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

	function findHeader(key, headers) {
		if (headers) {
			for (var i = 0; i < headers.length; i++) {
				var entry = headers[i];
				if (entry.Key === key) {
					return entry.value;
				}
			}
		}

		return null;
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
		if ($("#form-config-tab").hasClass("active")) {
			testConfig.UrlClientTarget = $("input[name='url-client-target']").val();
			testConfig.InputFieldName = $("input[name='input-field-name']").val();
			testConfig.SeleniumScript = $("textarea[name='selenium-script']").val();
			testConfig.FinalValidUrl = $("input[name='url-client-target-success']").val().split('#')[0];
			testConfig.HonestUserNeedle = $("input[name='honest-user-needle']").val();
			testConfig.EvilUserNeedle = $("input[name='evil-user-needle']").val();
			testConfig.ProfileUrl = $("input[name='user-profile-url']").val();
		} else if ($("#json-config-tab").hasClass("active")) {
			jQuery.extend(true, testConfig, JSON.parse($("#json-config").val()));
		}
	}

	function writeRPConfig(newTestRPConfig) {
		testConfig.UrlClientTarget = newTestRPConfig.UrlClientTarget;
		testConfig.InputFieldName = newTestRPConfig.InputFieldName;
		testConfig.SeleniumScript = newTestRPConfig.SeleniumScript;
		testConfig.FinalValidUrl = newTestRPConfig.FinalValidUrl;
		testConfig.HonestUserNeedle = newTestRPConfig.HonestUserNeedle;
		testConfig.EvilUserNeedle = newTestRPConfig.EvilUserNeedle;
		testConfig.ProfileUrl = newTestRPConfig.ProfileUrl;

		writeRPConfigGUI(newTestRPConfig);
	}

	function writeRPConfigGUI(newTestRPConfig) {
		// config form
		$("input[name='url-client-target']").val(newTestRPConfig.UrlClientTarget);
		$("input[name='input-field-name']").val(newTestRPConfig.InputFieldName);
		$("textarea[name='selenium-script']").val(newTestRPConfig.SeleniumScript);
		$("input[name='url-client-target-success']").val(newTestRPConfig.FinalValidUrl);
		$("input[name='honest-user-needle']").val(newTestRPConfig.HonestUserNeedle);
		$("input[name='evil-user-needle']").val(newTestRPConfig.EvilUserNeedle);
		$("input[name='user-profile-url']").val(newTestRPConfig.ProfileUrl);
		// JSON form
		let json = JSON.stringify(newTestRPConfig, jsonFilter, 4);
		$("#json-config").val(json);
	}

	function jsonFilter(key, val) {
		if (key === 'HonestWebfingerResourceId' || key === 'EvilWebfingerResourceId' || key === "HonestRpResourceId"
			|| key === "EvilRpResourceId" || key === 'Type') {
			// remove from json result
			return undefined;
		} else if (key === 'Client1Config' || key === 'Client2Config' || key === 'OPMetadata') {
			try {
				return JSON.parse(val);
			} catch (e) {
				console.log('JSON String to JSON conversion failed');
				return val;
			}
		}

		return val;
	}

	function updateOPConfig() {
		if ($("#form-config-tab").hasClass("active")) {
			testConfig.UrlOPTarget = $("#url-op-target").val();
			testConfig.OPMetadata = $("#op-metadata-json").val();
			testConfig.AccessToken1 = $("#registration-access-token-1").val();
			testConfig.AccessToken2 = $("#registration-access-token-2").val();
			testConfig.User2Name = $("#user-2-name").val();
			testConfig.User2Pass = $("#user-2-password").val();
			testConfig.User1Name = $("#user-1-name").val();
			testConfig.User1Pass = $("#user-1-password").val();
			testConfig.LoginScript =   $("#selenium-login-script").val();
			testConfig.ConsentScript = $("#selenium-consent-script").val();
			testConfig.Client1Config = $("#client-1-config").val();
			testConfig.Client2Config = $("#client-2-config").val();
		} else if ($("#json-config-tab").hasClass("active")) {
			// let jsonConfig = JSON.parse($("#json-config").val());
			let cfg = JSON.parse($("#json-config").val());
			if (cfg.Client1Config && (typeof cfg.Client1Config) !== "string") {
				cfg.Client1Config = JSON.stringify(cfg.Client1Config);
			}
			if (cfg.Client2Config && (typeof cfg.Client2Config) !== "string") {
				cfg.Client2Config = JSON.stringify(cfg.Client2Config);
			}
			if (cfg.OPMetadata && (typeof cfg.OPMetadata) !== "string") {
				cfg.OPMetadata = JSON.stringify(cfg.OPMetadata);
			}

			jQuery.extend(true, testConfig, cfg);
		}
	}

	function writeOPConfig(newTestOPConfig) {
		testConfig.UrlOPTarget = newTestOPConfig.UrlOPTarget;
		testConfig.OPMetadata = newTestOPConfig.OPMetadata;
		testConfig.AccessToken1 = newTestOPConfig.AccessToken1;
		testConfig.AccessToken2 = newTestOPConfig.AccessToken2;
		testConfig.User2Name = newTestOPConfig.User2Name;
		testConfig.User2Pass = newTestOPConfig.User2Pass;
		testConfig.User1Name = newTestOPConfig.User1Name;
		testConfig.User1Pass = newTestOPConfig.User1Pass;
		testConfig.LoginScript = newTestOPConfig.LoginScript;
		testConfig.ConsentScript = newTestOPConfig.ConsentScript;
		testConfig.Client1Config = newTestOPConfig.Client1Config;
		testConfig.Client2Config = newTestOPConfig.Client2Config;

		writeOPConfigGUI(newTestOPConfig);
	}

	function writeOPConfigGUI(newTestOPConfig) {
		$("#url-op-target").val(newTestOPConfig.UrlOPTarget);
		$("#op-metadata-json").val(newTestOPConfig.OPMetadata);
		$("#registration-access-token-1").val(newTestOPConfig.AccessToken1);
		$("#registration-access-token-2").val(newTestOPConfig.AccessToken2);

		$("#user-1-name").val(newTestOPConfig.User1Name);
		$("#user-1-password").val(newTestOPConfig.User1Pass);
		$("#user-2-name").val(newTestOPConfig.User2Name);
		$("#user-2-password").val(newTestOPConfig.User2Pass);

		$("#selenium-login-script").val(newTestOPConfig.LoginScript);
		$("#selenium-consent-script").val(newTestOPConfig.ConsentScript);
		$("#client-1-config").val(newTestOPConfig.Client1Config);
		$("#client-2-config").val(newTestOPConfig.Client2Config);
		// JSON form
		let jsonConf = JSON.stringify(newTestOPConfig, jsonFilter, 4);
		$("#json-config").val(jsonConf);
	}

	function processLearnResponse(learnResult) {
		var stepResult = learnResult.TestStepResult;
		var testPassed = stepResult.Result === "PASS";

		// update config
		if (learnResult.TestConfig) {
			if (testConfigType === RP_CONFIG_TYPE) {
				writeRPConfig(learnResult.TestConfig);
			}
			if (testConfigType === OP_CONFIG_TYPE) {
				writeOPConfig(learnResult.TestConfig);
			}
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

	function processTestResponse(stepContainer, stepResult) {
		var testPassed = stepResult.Result === "PASS";

		// update status
		var statusImgContainer = stepContainer.getElementsByClassName("status-image-container")[0];
		createResultImage(statusImgContainer, stepResult.Result);

		// write log
		var logContainer = stepContainer.getElementsByClassName("step-log")[0];
		writeLog(logContainer, stepResult.LogEntry, testPassed);
	}

	function stepTestError(stepId, stepContainer, xhr, status) {
		var result = createHttpErrorStepResult(xhr, status).TestStepResult;
		processTestResponse(stepContainer, result);
	}

	function showWaitDialog() {
		$("#please-wait-dialog").modal("show");
	}

	function hideWaitDialog() {
		$("#please-wait-dialog").modal("hide");
	}

	return module;

})(OPIV || {});
