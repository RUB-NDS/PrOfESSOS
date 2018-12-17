#FROM maven:3-jdk-8 as builder
#
#ENV HOME /opt/professos
#
## do we need all this???
##RUN RUN apt-get update && apt-get upgrade -y && apt-get install -y git wget  && apt-get clean && \
##        mkdir -p $HOME
#
#RUN mkdir -p $HOME
#WORKDIR $HOME
#
## copy project dir to container
#COPY ./src .
#COPY ./pom.xml .
#
#
## compile
#RUN mvn -Dmaven.javadoc.skip=true -Dmaven.test.skip=true clean package
#
#####

FROM jboss/wildfly:14.0.1.Final

# install phantomjs
USER root
RUN yum -y install fontconfig freetype freetype-devel fontconfig-devel libstdc++ bzip2 ca-certificates \ 
 && curl -L -O https://bitbucket.org/ariya/phantomjs/downloads/phantomjs-2.1.1-linux-x86_64.tar.bz2 \
 && mkdir -p /opt/phantomjs \
 && tar xjf phantomjs-2.1.1-linux-x86_64.tar.bz2 --strip-components 1 -C /opt/phantomjs/ \
 && rm -f phantomjs-2.1.1-linux-x86_64.tar.bz2 \ 
 && ln -sf /opt/phantomjs/bin/phantomjs /usr/local/bin/phantomjs

#ENV JAVA_OPTS='-Xms64m -Xmx512m -XX:MaxPermSize=256m -Djava.net.preferIPv4Stack=true -Djboss.modules.system.pkgs=org.jboss.byteman -Djava.awt.headless=true -agentlib:jdwp=transport=dt_socket,address=0.0.0.0:8787,server=y,suspend=n'
# ^ simply add --debug as option to standalone.sh

# copy the .war file from builder into wildfly's deployment folder
#COPY --from=builder /opt/professos/target/professos-1.0.0-SNAPSHOT.war /opt/jboss/wildfly/standalone/deployments/professos.war

# or use a prebuild .war from the host (e.g., to use local .m2/ maven-cache on host)
# generate .war using "mvn clean package" before running "docker build ."
COPY ./target/professos-1.0.0-SNAPSHOT.war /opt/jboss/wildfly/standalone/deployments/professos.war

# disable for debugging as root
USER jboss

# Expose the ports we're interested in
# using 8180 because we set a port-offset below
EXPOSE 8080

# uncomment below, if you need to acces the management interface
# EXPOSE 9990
# RUN /opt/jboss/wildfly/bin/add-user.sh admin admin --silent

# Set the default command to run on boot
# This will boot WildFly in the standalone mode and bind to all interfaces
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]

# for debugging, comment out the above CMD and uncomment the below
# adds --debug to standalone.sh and expose port 8787
#EXPOSE 8787
#CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0", "--debug"]

