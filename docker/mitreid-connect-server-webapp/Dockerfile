FROM maven:3-jdk-8 as builder
# based on https://github.com/ghchinoy/mitreidconnect-docker

ENV HOME /opt/mitreidc

# default to 1.3.x (latest stable);  master is the development branch.
# Override from docker build --build-arg if you want.
#ARG BRANCH=master
ARG BRANCH=1.3.x
ARG ISSUER=localhost:8080

RUN apt-get update && apt-get upgrade -y && apt-get install -y git wget sudo && apt-get clean && \
	mkdir -p $HOME

# Install
#USER mitreidc
WORKDIR $HOME

# fetch the source code using provided branch
RUN git clone --branch $BRANCH https://github.com/mitreid-connect/OpenID-Connect-Java-Spring-Server.git . 

#### this can be done in the next build stage by directly manipulating the .war archive - thus, a changed config
#### does not require us to recompile everything
## replace server config, user db, and clients db
#COPY ./config/server-config.xml $HOME/openid-connect-server-webapp/src/main/webapp/WEB-INF/server-config.xml
#RUN sed -i "s|{ISSUER}|$ISSUER|g" $HOME/openid-connect-server-webapp/src/main/webapp/WEB-INF/server-config.xml
#
#COPY ./config/users.sql $HOME/openid-connect-server-webapp/src/main/resources/db/hsql/users.sql
#COPY ./config/clients.sql $HOME/openid-connect-server-webapp/src/main/resources/db/hsql/clients.sql

# compile
RUN mvn -Dmaven.javadoc.skip=true -Dmaven.test.skip=true clean package

FROM jboss/wildfly:14.0.1.Final

## make sure bouncycastle is setup
#RUN cd $JAVA_HOME/jre/lib/ext/ && \
#	wget http://www.bouncycastle.org/download/bcprov-jdk15on-160.jar -nv && \
#	cd $JAVA_HOME/jre/lib/security && \
#	echo 'security.provider.10=org.bouncycastle.jce.provider.BouncyCastleProvider' >> $JAVA_HOME/jre/lib/security/java.security
#

# deploy compiled war to wildfly container
ENV HOME /opt/mitreidc
COPY --from=builder $HOME/openid-connect-server-webapp/target/openid-connect-server-webapp.war /opt/jboss/wildfly/standalone/deployments/openid-connect-server-webapp.war

# update config in .war, so we dont need to recompile when changing configs
COPY ./mitreid-connect-server-webapp/config .
# TODO this ovverrides the ISSUER that was set as build arg
RUN jar uf /opt/jboss/wildfly/standalone/deployments/openid-connect-server-webapp.war WEB-INF/server-config.xml
RUN jar uf /opt/jboss/wildfly/standalone/deployments/openid-connect-server-webapp.war WEB-INF/classes/db/hsql/users.sql
RUN jar uf /opt/jboss/wildfly/standalone/deployments/openid-connect-server-webapp.war WEB-INF/classes/db/hsql/clients.sql

## add a safeguard file that contains Professos' controller domain
COPY ./wildfly/static-professos.txt /opt/jboss/wildfly/static/static-professos.txt
## use a config that serves the static safeguard file
COPY ./wildfly/standalone.xml /opt/jboss/wildfly/standalone/configuration/standalone.xml
USER root
RUN chown jboss:jboss -R /opt/jboss/wildfly/static/static-professos.txt && \
    chown jboss:jboss -R /opt/jboss/wildfly/standalone/configuration/standalone.xml

USER jboss

WORKDIR $HOME/openid-connect-server-webapp
# Preload Jetty dependencies
#RUN mvn -Djetty.skip=true jetty:run

EXPOSE 8080

# uncomment below, if you need to access the management interface
# EXPOSE 9990
# RUN /opt/jboss/wildfly/bin/add-user.sh admin Admin#70365 --silent

# Set the default command to run on boot
# This will boot WildFly in the standalone mode and bind to all interfaces
#CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0", "--debug"]
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]

# build with: "sudo docker build -t mitreid-server-webapp:latest ."
# run with: "sudo docker run -ti -p 8080:8080 mitreid-server-webapp:latest"
#
# To set the Issuer ID to 'honestidp:8080', build with:
# "sudo docker build --build-arg ISSUER=honestidp.de:8080 -t mitreid-server-webapp:latest ."
# Configuration can then still be found at localhost:8080/openid-connect-server-webapp/.well-known/openid-configuration
# even though the IdP uses honestidp.de:8080 as issuer URI, so relative links will be broken.
# You may want to add honestidp.de to /etc/hosts as a WA
