FROM phusion/baseimage:0.11


ENV DEBIAN_FRONTEND noninteractive
ENV CERT_DIR /data/certs
ENV LETSENCRYPT_HOME /etc/letsencrypt



CMD ["/sbin/my_init"]

# Base setup
RUN apt-get -y update && \
    apt-get install -q -y curl apache2 software-properties-common && \
    add-apt-repository ppa:certbot/certbot && \
    apt-get -y update && \
    apt-get install -q -y python-certbot-apache python3-certbot-dns-cloudflare && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# configure PKI
COPY config/easyrsa/ /data/easyrsa

# configure apache
COPY config/templates/* /etc/apache2/sites-available/
RUN echo "ServerName localhost" >> /etc/apache2/conf-enabled/hostname.conf && \
	a2enmod ssl headers proxy proxy_http proxy_html xml2enc rewrite usertrack remoteip && \
	a2dissite 000-default default-ssl && \
	a2ensite controller honest_op evil_op honest_rp evil_rp test_op test_rp && \
	mkdir -p /var/lock/apache2 && \
	mkdir -p /var/run/apache2


# configure runit
RUN mkdir -p /etc/service/apache
COPY config/scripts/init_pki.sh /etc/my_init.d/
COPY config/scripts/run_apache.sh /etc/service/apache/run
COPY config/scripts/run_letsencrypt.sh /run_letsencrypt.sh
RUN chmod +x /*.sh && chmod +x /etc/my_init.d/*.sh && chmod +x /etc/service/apache/*


ARG USE_LETSENCRYPT
ARG WEBMASTER_MAIL
ARG STAGING

ARG CONTROLLER_HOST
ARG HONEST_OP_HOST
ARG EVIL_OP_HOST
ARG HONEST_RP_HOST
ARG EVIL_RP_HOST
ARG TEST_RP_HOST
ARG TEST_OP_HOST

# Manually set the apache environment variables in order to get apache to work immediately.
RUN echo $CERT_DIR > /etc/container_environment/CERT_DIR && \
    echo $USE_LETSENCRYPT > /etc/container_environment/USE_LETSENCRYPT && \
    echo $LETSENCRYPT_HOME > /etc/container_environment/LETSENCRYPT_HOME && \
    echo $WEBMASTER_MAIL > /etc/container_environment/WEBMASTER_MAIL && \
    echo $CONTROLLER_HOST > /etc/container_environment/CONTROLLER_HOST && \
    echo $HONEST_OP_HOST > /etc/container_environment/HONEST_OP_HOST && \
    echo $EVIL_OP_HOST > /etc/container_environment/EVIL_OP_HOST && \
    echo $HONEST_RP_HOST > /etc/container_environment/HONEST_RP_HOST && \
    echo $EVIL_RP_HOST > /etc/container_environment/EVIL_RP_HOST &&\
    echo $TEST_RP_HOST > /etc/container_environment/TEST_RP_HOST &&\
    echo $TEST_OP_HOST > /etc/container_environment/TEST_OP_HOST


# Stuff
EXPOSE 80
EXPOSE 443
VOLUME [ "/data", "$LETSENCRYPT_HOME", "/etc/apache2/", "/var/log/apache2" ]
