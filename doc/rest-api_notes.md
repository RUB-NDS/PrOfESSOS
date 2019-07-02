## Notes and examples regarding usage of REST API

### Create a TestObject

First, a new TestObject needs to be initialized. To this end, the `create-test-object` endpoint is used:

```
curl -X POST http://localhost:8080/api/op/create-test-object
```

This will return the TestObject which includes the test plan and, most importantly, the TestID. The TestID is to be used as path parameter in any following request.

#### Custom TestIDs
If enabled via the environment variable `OPIV_ALLOW_CUSTOM_TEST_ID`, a custom test id can be provided using the form-encoded body parameter `test_id`:

```
curl -X POST -d test_id=test-1-identityServer4 http://localhost:8080/api/op/create-test-object
```

_Tipp:_ On rare occasions, it may be helpful to set a custom TestId while using the Web-Frontend. This could be achieved in the developer console by calling:
```
OPIV.createTestPlan("OP-TestPlan","test_id=myCustomId")
```

## Run learning phase
Next, the test configuration must be supplied and evaluated. This can be done by using the `/{testId}/learn` endpoint. Be sure to use the TestId retrieved when creating the TestObject in the first step. 

```
Request URL: http://localhost:8080/api/rp/{testId}/learn
Request Method: POST
Status Code: 200 OK
Remote Address: [::1]:8080
Referrer Policy: no-referrer-when-downgrade


{"HonestWebfingerResourceId":"http://idp.oidc.honest-sso.de:8080/{testId}","EvilWebfingerResourceId":"http://idp.oidc.attack-sso.de:8080/{testId}","UrlClientTarget":"http://localhost:8080/portal?idp_verifier=1&response_type=code&sp_verifier=1","InputFieldName":"idp_url","SeleniumScript":"var opUrl = document.querySelector(\"input[name='idp_url']\");\nopUrl.value = \"§browser-input-op_url§\";\n// add short delay for taking screenshot\nsetTimeout(function() {opUrl.form.submit();}, 500);\n","FinalValidUrl":"https://honest-sp.com/oidc_sp/faces/success/index.xhtml","HonestUserNeedle":"honest-op-test-subject","EvilUserNeedle":"evil-op-test-subject","ProfileUrl":"http://honest-sp.com/oidc_sp/faces/success/index.xhtml","Type":"de.rub.nds.oidc.test_model.TestRPConfigType"}
```

Note that the members `HonestWebfingerResourceId` and `EvilWebfingerResourceId` depend on the TestID.

## Set config

```
curl -vv -d @/tmp/test.json -H "Content-Type: application/json" http://localhost:8080/api/rp/{testId}/config
```

Unless a complete (and completely valid config) is submitted, you will still need to run the learning phase to complete server side configuration steps.

_Note:_ The configuration for OP tests may include the optional members `Client1Config` and `Client2Config`. These must be string representations of the JSON configuration, i.e., quotes need to be escaped.


## Read Config for TestID

```
curl -vv http://localhost:8080/api/rp/{testId}/config  
```
 
## Start a single Test

```
curl -vv -X POST http://localhost:8080/api/rp/{testId}/test/ID%20Spoofing%201
```
This requires that a valid config has been set beforehand using the `/set-config` or `/learn` endpoints.


## Export TestObject 
Includes Testresults, if run. Set the `Accept` header to JSON or XML to retrieve the report in the respective format.

```
curl -vv -H "Accept: application/json" http://localhost:8080/api/rp/{testId}/export
```

