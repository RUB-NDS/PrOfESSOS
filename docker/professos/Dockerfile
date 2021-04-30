FROM ubuntu:18.04

# install chromedriver, chromium
USER root
RUN apt-get update && apt-get install -y curl vim openjdk-11-jdk chromium-browser chromium-chromedriver \ 
 && apt-get clean

###
# setup wildfly as per https://github.com/jboss-dockerfiles/wildfly/blob/master/Dockerfile
RUN groupadd -r jboss -g 1000 && useradd -u 1000 -r -g jboss -m -d /opt/jboss -s /sbin/nologin -c "JBoss user" jboss && \
    chmod 755 /opt/jboss

# Set the working directory to jboss' user home directory
WORKDIR /opt/jboss

# Set the WILDFLY_VERSION env variable
ENV WILDFLY_VERSION 17.0.0.Final
ENV JBOSS_HOME /opt/jboss/wildfly

RUN cd $HOME \
    && curl -O https://download.jboss.org/wildfly/$WILDFLY_VERSION/wildfly-$WILDFLY_VERSION.tar.gz \
    && tar xf wildfly-$WILDFLY_VERSION.tar.gz \
    && mv $HOME/wildfly-$WILDFLY_VERSION $JBOSS_HOME \
    && rm wildfly-$WILDFLY_VERSION.tar.gz \
    && chown -R jboss:0 ${JBOSS_HOME} \
    && chmod -R g+rw ${JBOSS_HOME}

# Ensure signals are forwarded to the JVM process correctly for graceful shutdown
ENV LAUNCH_JBOSS_IN_BACKGROUND true

# add wildfly config (enables proxy-address-forwarding in default http listener; required when Wildfly runs
# behind a TLS terminating reverse proxy)
COPY ./config/standalone.xml /opt/jboss/wildfly/standalone/configuration/standalone.xml

# copy ca certificates
COPY ./config/*.crt /usr/local/share/ca-certificates/
RUN chmod 0644 /usr/local/share/ca-certificates/*
RUN update-ca-certificates

ARG CONTROLLER_HOST
ARG HONEST_OP_HOST
ARG EVIL_OP_HOST
ARG HONEST_RP_HOST
ARG EVIL_RP_HOST
ARG TEST_RP_HOST
ARG TEST_OP_HOST
# optional
ARG SKIP_TARGET_GRANT="false"
ARG DISABLE_TLS_TRUST_CHECK="false"
ARG PRINT_TEST_ID_PATH="true"
ARG TEST_ID_LENGTH=8

COPY ./config/professos.conf /opt/jboss/professos-template.conf
RUN perl -p -e 's/\$\{(\w+)\}/(exists $ENV{$1}?$ENV{$1}:"missing variable $1")/eg' < /opt/jboss/professos-template.conf > /opt/jboss/professos.conf

# copy the .war file into wildfly's deployment folder
ARG PROFESSOS_WAR="./build/professos.war"
COPY $PROFESSOS_WAR /opt/jboss/wildfly/standalone/deployments/professos.war

# disable for debugging as root
USER jboss

# Expose the ports we're interested in
EXPOSE 8080
# port 8787 for debugging WildFly 
EXPOSE 8787

# uncomment below, if you need to acces the management interface
# EXPOSE 9990
# RUN /opt/jboss/wildfly/bin/add-user.sh admin admin --silent

# uncomment to enable verbose debug logs
#RUN /opt/jboss/wildfly/bin/jboss-cli.sh --commands="embed-server, /subsystem=logging/console-handler=CONSOLE:write-attribute(name=level, value=DEBUG)"
#RUN /opt/jboss/wildfly/bin/jboss-cli.sh --commands="embed-server, /subsystem=logging/root-logger=ROOT:write-attribute(name=level, value=DEBUG)"

# Set the default command to run on boot
# This will boot WildFly in the standalone mode and bind to all interface
ENTRYPOINT ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0", "--debug", "0.0.0.0:8787"]
