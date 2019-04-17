# Setup Instructions

## Local Development Setup Using Docker

First, compile the project by running `mvn clean package` from the main folder. Next, run `docker-compose up --build`. This will create three docker containers: one for PrOfESSOS itself, one for the demo Relying Party and one running the demo Identity Provider. Both, RP and IdP will take some time till the build finishes for the first time, as the projects are fetched from Github and compiled within their respective containers.

The docker-compose file `docker-compose.override.yml` is used by default and will configure a mount volume in such a way that the directory `${project.basedir}/target/dev-deploy` from the Maven target is mounted into Wildly's standalone deployment folder. In consequence, each time `mvn package` is run, Wildfly deploys the new version of the `.war` file. The benefit of this method is that it does not depend on the IDE used.
**Note:** Do not run `mvn clean` while the container is running, as this would also delete Wildlfy internal files. If you accidentally ran `mvn clean` while the container was running just shut it down, run `mvn clean package` and restart the containers with `docker-compose up`.

To connect a debugger, Wildfly's debugging port `8787` is exposed to the host and the `--debugging` flag is set in Docker's default command.

### TLS 
The demo services do not use TLS and are communicating through the docker bridge network `professos-default-net`. To intercept the message flow between PrOfESSOS and the demo services using Wireshark or a similar tool for network sniffing, figure out the docker network ID using `docker network ls`. Note that this will not include PrOfESSOS internal traffic such as between the Selenium WebDriver and PrOfESSOS OPs.

To enable TLS support, there is an experimental branch called `nginx-proxy-tls` which adds an nginx container to the setup. The nginx container is used for TLS terminating reverse proxy using self-issued certificates. A testtarget needs to trust the CA certificate stored in `docker/nginx/certs/professos_ca.crt`. As all traffic goes through proxy, the CA cert should also be trusted by the UserAgent (i.e. browser or HttpClient that is using PrOfESSSOS' API)

### Environment
A few environment variables are read by PrOfESSOS and can be used for configuration. Currently, the following variables are recognized:


| Variable  | Description  |
|---|---|
| OPIV_ALLOW_CUSTOM_TEST_ID  | If set to `true`, the API endpoint `/create-test-object` accepts a POST parameter `test_id` that can be used to specify the testId PrOfESSOS uses for the test object to be created. This especially useful when testing providers that do not support dynamic registration, as the testId is part of the `redirect_uri`  |   
| OPIV_TARGET_GRANT_NOT_NEEDED  | Quickswitch to disable the check for a `.professos` file at the test target's root path. The file is otherwise required to contain the URL of PrOfESSOS to make sure the test target permitted the test run. This check coul also be disabled in the testplans (`rp_testplan.xml`,`op_testplan.xml`) |
| OPIV_TRUST_ALL_TLS | Disable certificate verification for backchannel communication. For example, if PrOfESSOS' OP-Verifier runs against an OP that serves an untrusted or invalid certificate, this environment variable must be set to `true` to enable direct connections, e.g., to the Discovery or Token Endpoint |

### Integration Tests

A few basic integration tests against the demo services are provided. TestNG is used to run a number of PrOfESSOS 
TestSteps, verify the expected result as well as performing a basic pattern matching against the JSON response. This is 
not a comprehensive test suite, nevertheless it may help to detect unwittingly introduced changes or regreessions early on.
 
The test methods are annotated with TestNG groups to allow for varying test scenarios: run tests against manually started 
 PrOfESSOS and demo service instances or automatically spin up the docker setup using docker-compose before running the tests.
 Following groups are defined
 
 * `rp-it`: Used to start the RelyingParty integration tests. The RP as well as PrOfESSOS must be
 running as configured in the default docker-compose file `docker-compose.override.yml`, that is PrOfESSOS must be 
 available at `localhost:8080` and needs to be able to connect to the RP at `www.honestsp.de:8080`.
 * `op-it`: Run the OpenID Provider integration tests against (manually started) running services. Again, the services should be
 configured similarly to the default docker-compose file.
 * `docker-rp`, `docker-op`: Automatically starts the docker-compose profile using the `testcontainers` package and run 
 the respective tests.

Currently, the integration tests are not bound to a Maven lifecycle phase to not start the long-running integration tests 
during every build. Therefore, the integration tests need to be started manually, either by
 configuring the used IDE or using the bundled `maven-failsafe` plugin. To start the Relying Party tests against running services, user:
```
mvn -Dgroups=rp-it failsafe:integration-test
```

To run the Relying Party tests against automatically started testcontainers, use:
```
mvn -Dgroups=docker-rp failsafe:integration-test
```


---

Some configurations for the demo RP and Idp can be adjusted in `./docker/simple-web-app/config/` and `./docker/mitreid-connect-server-webapp/config/*`. Do not forget to run `docker-compose build` after changing the demo service configuration.


## Production

The docker-compose file `docker-compose.prod.yml` contains basic configuration that should be adjusted to the specific requirements of the deployment. Its main purpose is to not expose debugging ports as it is done in the override file for development. Furthermore, the demo RP and the demo IdP are not part of the composed setup.

The compose file must explicitly provided as argument to make use of this configuration:
```
docker-compose -f docker-compose.prod.yml up --build
```
Also note that the compiled .war archive is not mounted into the container when using the productive docker-compose configuration. For this reason the container needs to be recreated after any changes have been made to the web archive.

*TODO*
- adjust the settings in `src/main/resources/servernames.properties` if necessary
- TLS termination using a reverse proxy (config examples?)
- to be continued...
