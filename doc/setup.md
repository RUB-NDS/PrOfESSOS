# Setup Instructions

## Local Development Setup Using Docker

First, compile the project by running `mvn clean package` from the main folder. Next, run `docker-compose up --build`. This will create three docker containers: one for PrOfESSOS itself, one for the demo Relying Party and one running the demo Identity Provider. Both, RP and IdP will take some time till the build finishes for the first time, as the projects are fetched from Github and compiled within their respective containers.

The docker-compose file `docker-compose.override.yml` is used by default and will configure a mount volume in such a way that the directory `${project.basedir}/target/dev-deploy` from the Maven target is mounted into Wildly's standalone deployment folder. In consequence, each time `mvn package` is run, Wildfly deploys the new version of the `.war` file. The benefit of this method is that it does not depend on the IDE used.
**Note:** Do not run `mvn clean` while the container is running, as this would also delete Wildlfy internal files. If you accidentally ran `mvn clean` while the container was running just shut it down, run `mvn clean package` and restart the containers with `docker-compose up`.

To connect a debugger, Wildfly's debugging port `8787` is exposed to the host and the `--debugging` flag is set in Docker's default command.

The demo services do not use TLS and are communicating through the docker bridge network `professos-default-net`. To intercept the message flow between PrOfESSOS and the demo services using Wireshark or a similar tool for network sniffing, figure out the docker network ID using `docker network ls`. Note that this will not include PrOfESSOS internal traffic such as between the Selenium WebDriver and PrOfESSOS OPs.

---

Some configurations for the demo RP and Idp can be adjusted in `./docker/simple-web-app/config/` and `./docker/mitreid-connect-server-webapp/config/*`. Do not forget to run `docker-compose build` after changing the demo service configuration.


## Production


The docker-compose file `docker-compose.prod.yml` contains basic configuration that should be adjusted ot the specific requirements of the deployment. Its main purpose is to not expose debugging ports as it is done in the override file for development.

*TODO*
- adjust the URLs in `src/main/resources/servernames.properties` if necessary
- TLS termination using a reverse proxy (config examples?)
- tbc ...
