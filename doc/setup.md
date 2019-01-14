# Setup Instructions

## Local Development Setup Using Docker

First, compile the project by running `mvn clean package` from the main folder. Next, run `docker-compose build`. This will create three docker containers: one for PrOfESSOS itself, one for the demo Relying Party and one running the demo Identity Provider. Both, RP and IdP will take some till the build finishes for the first time, as the projects are fetched from Github and compiled within their respective containers.

If you changed some code and you want to update the running professos container, use 
```
mvn clean package && sudo docker-compose up -d --no-deps --build professos
```
This will rebuild the maven project, rebuild the professos docker image and restart its container. 

Some configurations for the demo RP and Idp can be adjusted in `./docker/simple-web-app/config/` and `./docker/mitreid-connect-server-webapp/config/*`.

## Production

*TODO* 


- adjust the URLs in `src/main/resources/servernames.properties` if necessary
- TLS termination using a reverse proxy (config examples?)
- tbc ...
