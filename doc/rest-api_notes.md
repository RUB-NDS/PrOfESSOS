# Notes regarding usage of REST API

## Run learning phase
```
Request URL: http://localhost:8080/api/rp/Zau9rdWlNgEvMWgAvcWDUw/learn
Request Method: POST
Status Code: 200 OK
Remote Address: [::1]:8080
Referrer Policy: no-referrer-when-downgrade


{"HonestWebfingerResourceId":"http://idp.oidc.honest-sso.de:8080/dispatch/Zau9rdWlNgEvMWgAvcWDUw","EvilWebfingerResourceId":"http://idp.oidc.attack-sso.de:8080/dispatch/Zau9rdWlNgEvMWgAvcWDUw","UrlClientTarget":"http://localhost:8080/portal?idp_verifier=1&response_type=code&sp_verifier=1","InputFieldName":"idp_url","SeleniumScript":"var opUrl = document.querySelector(\"input[name='idp_url']\");\nopUrl.value = \"§browser-input-op_url§\";\n// add short delay for taking screenshot\nsetTimeout(function() {opUrl.form.submit();}, 500);\n","FinalValidUrl":"https://honest-sp.com/oidc_sp/faces/success/index.xhtml","HonestUserNeedle":"honest-op-test-subject","EvilUserNeedle":"evil-op-test-subject","ProfileUrl":"http://honest-sp.com/oidc_sp/faces/success/index.xhtml","Type":"de.rub.nds.oidc.test_model.TestRPConfigType"}
```

Note that the members `HonestWebfingerResourceId` and `EvilWebfingerResourceId` depend on the TestID.

## Set config

```
curl -vv -d @/tmp/test.json -H "Content-Type: application/json" http://localhost:8080/api/rp/q8tEGhZabUW__lSjIIeM9g/config
```

You will still need to run the learning phase to complete server side configuration steps.

## Read Config for TestID

```
curl -vv -H "Content-Type: application/json" http://localhost:8080/api/rp/q8tEGhZabUW__lSjIIeM9g/config  
```
 
## Start a single Test

```
curl -vv -X POST -H "Content-Type: application/json" http://localhost:8080/api/rp/q8tEGhZabUW__lSjIIeM9g/test/ID%20Spoofing%201
```

## Export TestObject 
Includes Testresults, if run. Content Type can be set to JSON or XML

```
curl -vv -H "Content-Type: application/json" http://localhost:8080/api/rp/VfmRgMibwPJY4u-AvnG-HA/export
```

