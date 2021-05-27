FROM maven:3-jdk-8 as builder

ENV HOME /opt/mitreidc-sp

# Override from docker build --build-arg if you want.
ARG BRANCH=master

RUN apt-get update && apt-get upgrade -y && apt-get install -y git wget sudo && apt-get clean && \
        mkdir -p $HOME

# Install
WORKDIR $HOME

# fetch the source code using provided branch
RUN git clone --branch $BRANCH https://github.com/mitreid-connect/simple-web-app.git .

# update config
#COPY ./config/servlet-context.xml $HOME/src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml

# compile simple-web-app without generating doc and tests
RUN mvn -Dmaven.javadoc.skip=true -Dmaven.test.skip=true clean package

#####

FROM jboss/wildfly:14.0.1.Final

USER root
RUN yum -y install perl
USER jboss

ENV HOME /opt/mitreidc-sp

# copy demo client to deployment folder
COPY --from=builder $HOME/target/simple-web-app.war /opt/jboss/wildfly/standalone/deployments/simple-web-app.war

# update config in .war, so we dont need to recompile when updating config
USER root
ARG TEST_RP_HOST
COPY ./config/servlet-context-template.xml ./servlet-context-template.xml
RUN mkdir -p WEB-INF/spring/appServlet/
RUN perl -p -e 's/\$\{(\w+)\}/(exists $ENV{$1}?$ENV{$1}:"missing variable $1")/eg' < ./servlet-context-template.xml > WEB-INF/spring/appServlet/servlet-context.xml
USER jboss
RUN jar uf /opt/jboss/wildfly/standalone/deployments/simple-web-app.war WEB-INF/spring/appServlet/servlet-context.xml

## add a safeguard file that contains controller domain
ARG CONTROLLER_URL
RUN mkdir -p /opt/jboss/wildfly/static/
RUN echo "$CONTROLLER_URL" > /opt/jboss/wildfly/static/static-professos.txt
USER root
RUN chown jboss:jboss -R /opt/jboss/wildfly/static/static-professos.txt
# add wildfly config that serves the static safeguard file
COPY ./config/standalone.xml /opt/jboss/wildfly/standalone/configuration/standalone.xml

# copy ca certificates
COPY ./config/*.crt /etc/pki/ca-trust/source/anchors/
RUN chmod 0644 /etc/pki/ca-trust/source/anchors/*
RUN update-ca-trust

# comment, to allow debugging as root
USER jboss

# Expose the ports we're interested in
EXPOSE 8080

# uncomment below, if you need to acces the management interface
# EXPOSE 9990
# RUN /opt/jboss/wildfly/bin/add-user.sh admin Admin#70365 --silent

# Set the default command to run on boot
# This will boot WildFly in the standalone mode and bind to all interface
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]
