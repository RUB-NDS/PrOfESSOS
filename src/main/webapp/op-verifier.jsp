<%--
    Document   : op-verifier
    Created on : 01.07.2019, 17:41:50
    Author     : Tobias Wich
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>

<!DOCTYPE html>
<!--
Copyright 2016 Ruhr-Universität Bochum.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
<html lang="en">
<head>
	<meta charset="utf-8">
	<meta http-equiv="X-UA-Compatible" content="IE=edge">
	<meta name="viewport" content="width=device-width, initial-scale=1.0">

	<title>PrOfESSOS</title>


	<link rel="stylesheet" href="webjars/bootstrap/4.3.1/css/bootstrap.min.css">

	<link rel="stylesheet" href="css/opiv.css">
</head>
<body>
	<nav class="navbar navbar-expand-md navbar-dark fixed-top bg-dark">
		<a class="navbar-brand" href="./">PrOfESSOS</a>
		<div id="navbar" class="collapse navbar-collapse">
			<ul class="navbar-nav">
				<li class="nav-item"><a class="nav-link" href="rp-verifier.html">Client Verifier</a></li>
				<li class="active nav-item"><a class="nav-link" href="#" onclick="OPIV.clear();">OP Verifier</a></li>
			</ul>
		</div>
	</nav>

<!-- Progress Modal -->
<div class="modal fade" id="please-wait-dialog" tabindex="-1" role="dialog" data-backdrop="static">
	<div class="modal-dialog" role="document">
		<div class="modal-content">
			<div class="modal-header">
				<!--
                <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                    <span aria-hidden="true">&times;</span>
                </button>
                -->
				<h4 class="modal-title" id="myModalLabel">Process running</h4>
			</div>
			<div class="modal-body">
				<p>Please wait ...</p>
				<div class="progress">
					<div class="progress-bar progress-bar-info progress-bar-striped active" role="progressbar"
						 aria-valuenow="100" aria-valuemin="0" aria-valuemax="100"
						 style="width: 100%">
						<span class="sr-only">Unknown Completion</span>
					</div>
				</div>
			</div>
			<!-- Cancel is not implemented right now, so disable the cancel button -->
			<!--
            <div class="modal-footer">
                <button type="button" class="btn btn-primary" onclick="OPIV.cancelProcess();">Cancel</button>
            </div>
            -->
		</div>
	</div>
</div>

<!--
<div class="modal fade" id="please-wait-dialog" tabindex="-1" role="dialog">
    <div class="modal-header">
        <h1>Processing...</h1>
    </div>
    <div class="modal-body">
        <div class="progress progress-striped active">
            <div class="bar" style="width: 100%;"></div>
        </div>
    </div>
</div>
-->

<div class="container">

	<div class="page-headline">
		<h1>PrOfESSOS IdP Verifier</h1>
		<p class="lead"></p>
	</div>

	<h2>About PrOfESSOS</h2>
	<p>PrOfESSOS (Practical Offensive Evaluation of Single Sign-On Services) is an open source tool for
		fully automated Evaluation-as-a-Service of OpenID Connect clients and Identity Providers. The tool has been developed
		for the research paper <em>SoK: Single Sign-On Security â An Evaluation of OpenID Connect</em>,
		<a href="http://www.ieee-security.org/TC/EuroSP2017/">EuroS&P 2017</a>.
	</p>
	<p>The source code of PrOfESSOS can be found at GitHub.<br>
		<a href="https://github.com/RUB-NDS/PrOfESSOS">https://github.com/RUB-NDS/PrOfESSOS</a>
	</p>

	<h2>Prerequisite to use PrOfESSOS</h2>
	<p>As a safeguard to prevent illegitimate usage of the PrOfESSOS service, the OP operator must install a file
		named <i>.professos</i> at the root directory of the webserver (see <i>Login-Site URL</i> below) containing the
		base URL of the PrOfESSOS service (<code id="controller-uri">&lt;PrOfESSOS-URI&gt;</code>).
		See <a href="http://www.honestsp.de:8080/.professos" target="_blank">http://www.honestsp.de:8080/.professos</a>
		for an example of such a file.
	</p>

	<h2>How to use the Demo Site?</h2>
	<ol>
		<li>Click on the <i>Load Demo Config</i> button.</li>
		<ul>
			<li>This fills out the URL of the tested Identity Provider (IdP/OP) and
				completes Stage 1 of PrOfESSOS.</li>
		</ul>
		<li>Cick on the button <i>Learn</i></li>
		<ul>
			<li>This starts the configuration evaluation and completes Stage 2.</li>
		</ul>
		<li>Cick on the button <i>Run all Tests</i> to complete Stage 3.</li>
		<ul>
			<li>This starts all available tests, and thus, executes the attacks.</li>
		</ul>
	</ol>

	<form id="op-demo-form" action="javascript:;" onsubmit="javascript:OPIV.loadDemo();">
		<button class="btn btn-primary" type="submit" value="Load Demo Config">Load Demo Config</button>
	</form>

	<hr>

	<h2>Legend</h2>
	<dl class="row">
		<dt class="col-sm-1"><img alt="NOT_RUN" src="img/NOT_RUN.png" width="20"></dt>
		<dd class="col-sm-11"><b>Test not run</b></dd>
		<dt class="col-sm-1"><img alt="PASS" src="img/PASS.png" width="20"></dt>
		<dd class="col-sm-11"><b>Test passed</b></dd>
		<dt class="col-sm-1"><img alt="FAIL" src="img/FAIL.png" width="20"></dt>
		<dd class="col-sm-11"><b>Test failed (Attack succeeded)</b></dd>
		<dt class="col-sm-1"><img alt="UNDETERMINED" src="img/UNDETERMINED.png" width="20"></dt>
		<dd class="col-sm-11"><b>Test outcome undetermined</b></dd>
	</dl>

	<hr>

	<h2>Stage 1: Setup - OpenID Provider Parameters</h2>
	<div class="row">
		<div class="learn-controls col-sm-12">
			<legend>Client Parameters</legend>
			<p>Test ID: <code id="test-id-display">not-loaded</code><br>
				Honest RP Identity: <code id="honest-rp-id-display">not-loaded</code><br>
				Evil RP Identity: <code id="evil-rp-id-display">not-loaded</code></p>

			<legend>Target OP Parameters</legend>

			<nav class="nav nav-tabs">
				<a class="active nav-item nav-link" data-toggle="tab" href="#form-config-tab">HTML Form</a>
				<a class="nav-item nav-link" data-toggle="tab" href="#json-config-tab">JSON</a>
			</nav>
			<div class="tab-content">
				<div id="form-config-tab" class="tab-pane fade show active">

					<form class="form-horizontal" id="op-learn-form" action="javascript:;" onsubmit="javascript:OPIV.learnOP();">

							<div class="container col-sm-12" id="config-accordion">
								<div class="panel-group" id="accordion">
									<div class="panel panel-default">
										<div class="panel-heading" data-toggle="collapse" data-parent="#accordion" href="#collapse1" aria-expanded="true">
											<h4 class="panel-title">Dynamic Registration</h4>
										</div>
										<div id="collapse1" class="panel-collapse collapse in">
											<div class="panel-body">

												<div class="form-group">
													<label class="control-label col-sm-2" for="url-op-target">Issuer (OP) URL:</label>
													<div class="col-sm-10">
														<input class="form-control" type="url" name="url-op-target" id="url-op-target" form="op-learn-form" size="80">
													</div>
												</div>

											</div>
										</div>
									</div>
									<div class="panel panel-default">
										<div class="panel-heading" data-toggle="collapse" data-parent="#accordion" href="#collapse2" aria-expanded="false">
											<h4 class="panel-title">Manual Configuration</h4>
										</div>
										<div id="collapse2" class="panel-collapse collapse">
											<div class="panel-body">

												<div class="form-group">
													<label class="control-label col-sm-2" for="op-metadata-json">OP Metadata (JSON)</label>
													<div class="col-sm-10">
														<textarea class="form-control" type="text" name="op-metadata-json" id="op-metadata-json" form="op-learn-form" rows="5" size="80"></textarea>
													</div>
												</div>
												<div class="form-group">
													<label class="control-label col-sm-2" for="registration-access-token-1">Reg. AccessToken 1:</label>
													<div class="col-sm-10">
														<input class="form-control" type="text" id="registration-access-token-1" name="registration-access-token-1" form="op-learn-form" size="80">
													</div>
												</div>
												<div class="form-group">
													<label class="control-label col-sm-2" for="registration-access-token-2">Reg. AccessToken 2:</label>
													<div class="col-sm-10">
														<input class="form-control" type="text" id="registration-access-token-2" name="registration-access-token-2" form="op-learn-form" size="80">
													</div>
												</div>
												<legend>Client Config</legend>
												<!--TODO add Tooltipps: Config in JSON, e.g. as received in a registration response-->
												<div class="form-group">
													<label class="control-label col-sm-2" for="client-1-config">Client-1 Config (JSON)</label>
													<div class="col-sm-10">
														<textarea class="form-control" type="text" id="client-1-config" name="client-1-config" form="op-learn-form" rows="5" cols="80"></textarea>
													</div>
												</div>
												<div class="form-group">
													<label class="control-label col-sm-2" for="client-2-config">Client-2 Config (JSON)</label>
													<div class="col-sm-10">
														<textarea class="form-control" type="text" id="client-2-config" name="client-2-config" form="op-learn-form" rows="5" cols="80"></textarea>
													</div>
												</div>



											</div>
										</div>
									</div>

								</div>
							</div>


							<legend>Login/Consent Scripts</legend>
							<!--TODO add field for Username-Formfieldname and Password-Formfieldname ??? -->
							<div class="form-group">
								<label class="control-label col-sm-2" for="selenium-login-script">Login-Script:</label>
								<div class="col-sm-10">
									<textarea class="form-control" name="selenium-login-script" id="selenium-login-script" form="op-learn-form" rows="5" cols="80"></textarea>
								</div>
							</div>
							<div class="form-group">
								<label class="control-label col-sm-2" for="selenium-consent-script">Consent-Script:</label>
								<div class="col-sm-10">
									<textarea class="form-control" name="selenium-consent-script" id="selenium-consent-script" form="op-learn-form" rows="5" cols="80"></textarea>
								</div>
							</div>
							<legend>EndUser/ResourceOwner Credentials</legend>
							<div class="form-group">
								<label class="control-label col-sm-2" for="user-1-name">Username 1:</label>
								<div class="col-sm-10">
									<input class="form-control" type="text" name="user-1-name" id="user-1-name" form="op-learn-form" required size="80">
								</div>
							</div>
							<div class="form-group">
								<label class="control-label col-sm-2" for="user-1-password">User 1 Password:</label>
								<div class="col-sm-10">
									<input class="form-control" type="text" name="user-1-password" id="user-1-password" form="op-learn-form" required size="80">
								</div>
							</div>
							<div class="form-group">
								<label class="control-label col-sm-2" for="user-2-name">Username 2:</label>
								<div class="col-sm-10">
									<input class="form-control" type="text" name="user-2-name" id="user-2-name" form="op-learn-form" required size="80">
								</div>
							</div>
							<div class="form-group">
								<label class="control-label col-sm-2" for="user-2-password">User 2 Password:</label>
								<div class="col-sm-10">
									<input class="form-control" type="text" name="user-2-password" id="user-2-password" form="op-learn-form" required size="80">
								</div>
							</div>

							<div class="col-sm-2"></div>
							<div class="col-sm-10">

						</div>
					</form>

				</div>



				<div id="json-config-tab" class="tab-pane fade">
					<form class="form-horizontal" id="op-learn-json-form" action="javascript:;" onsubmit="javascript:OPIV.learnOP();">
						<!--<fieldset>-->

						<!--<legend>Client Parameters JSON</legend>-->
						<div class="form-group">
							<label class="control-label col-sm-2" for="json-config">JSON Config:</label>
							<div class="col-sm-10">
								<textarea class="form-control" type="text" name="json-config" id="json-config" form="op-learn-json-form" required rows="10" cols="80"></textarea>
							</div>

						</div>

						<div class="col-sm-2"></div>
						<div class="col-sm-10">
						</div>
						<!--</fieldset>-->
					</form>
				</div>
			</div>
		</div>
	</div>


	<h2>Stage 2: Configuration Evaluation</h2>
	<!--<button class="btn btn-primary" type="submit" value="Learn" form="op-learn-form"  onclick="javascript:OPIV.submitOPLearningForm()">Learn</button>-->
	<button class="btn btn-primary" type="button" value="Learn" onclick="javascript:OPIV.submitOPLearningForm()">Learn</button>

	<div id="learn-report">
		<script>
            function toggleLearnLog() {
                var containerToHide = document.getElementById("learn-log");
                if (containerToHide.style.display === "none") {
                    containerToHide.style.display = null;
                } else {
                    containerToHide.style.display = "none";
                }
            }
		</script>
		<a onclick="toggleLearnLog();">
			<b>Learning Log</b>
			<img id="learn-status" alt="NOT_RUN" src="img/NOT_RUN.png">
		</a>
		<div id="learn-log">

		</div>
	</div>


	<h2>Stage 3: Tests and Attacks</h2>
	<form id="op-runall-form" action="javascript:;" onsubmit="javascript:OPIV.runAllTests();">
		<button class="btn btn-primary" type="submit" value="Run all Tests" form="op-runall-form">Run all Tests</button>
	</form>

	<div id="test-report">
		<!-- The Test Report is filled by the JS logic -->
	</div>

</div>

<script src="webjars/jquery/3.4.1/jquery.min.js">
</script>

<script src="webjars/bootstrap/4.3.1/js/bootstrap.min.js">
</script>

<!-- own scripts -->
<script src="js/verify-controller.js"></script>
<script>
    // init dynamic elements on this page
    document.getElementById("controller-uri").innerHTML = location.origin;

    // init the test session
    OPIV.createTestPlan("OP-TestPlan");
</script>
</body>
</html>
