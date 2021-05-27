#!/bin/bash

STAGE_PARAM=
if [ ! -z "$STAGING" ]
then
  echo "Using Let's Encrypt Staging environment..."
  STAGE_PARAM="--staging"
fi

PLUGIN_PARAM="--apache"
if [ -r "/data/cloudflare.conf" ]
then
  PLUGIN_PARAM="--dns-cloudflare --dns-cloudflare-credentials /data/cloudflare.conf"
fi


certbot certonly $STAGE_PARAM $PLUGIN_PARAM -n --agree-tos --email $WEBMASTER_MAIL -d "$@"
