#!/bin/bash

EASYRSA_DIR=/data/easyrsa

init_le()
{
    echo "Init Let's encrypt ..."

    # init only if lets-encrypt is running for the first time and if DOMAINS was set
    if [ ! -d $LETSENCRYPT_HOME/live ]
    then
        create_le_cert $CONTROLLER_HOST controller-ssl
        create_le_cert $HONEST_OP_HOST honest_op-ssl
        create_le_cert $EVIL_OP_HOST evil_op-ssl
        create_le_cert $HONEST_RP_HOST honest_rp-ssl
        create_le_cert $EVIL_RP_HOST evil_rp-ssl
        create_le_cert $TEST_OP_HOST test_op-ssl
        create_le_cert $TEST_RP_HOST test_rp-ssl
    fi
}

create_le_cert()
{
    echo "Creating certificate for host $1 ..."
    local NAME=$1
    if [ ! -z $NAME ]
    then
        /run_letsencrypt.sh $NAME
        ln -s $LETSENCRYPT_HOME/live/$NAME/fullchain.pem $CERT_DIR/$NAME.crt
        ln -s $LETSENCRYPT_HOME/live/$NAME/privkey.pem $CERT_DIR/$NAME.key
        a2ensite $2
    fi
}



init_easyrsa()
{
    echo "Init easyrsa ..."

    if [ ! -r $CERT_DIR/$CONTROLLER_HOST.crt ]
    then
        create_easy_cert $CONTROLLER_HOST controller-ssl
        create_easy_cert $HONEST_OP_HOST honest_op-ssl
        create_easy_cert $EVIL_OP_HOST evil_op-ssl
        create_easy_cert $HONEST_RP_HOST honest_rp-ssl
        create_easy_cert $EVIL_RP_HOST evil_rp-ssl
        create_easy_cert $TEST_OP_HOST test_op-ssl
        create_easy_cert $TEST_RP_HOST test_rp-ssl
    fi
}

create_easy_cert()
{
    echo "Creating certificate for host $1 ..."
    local NAME=$1
    del_cert_links $NAME
    cd $EASYRSA_DIR
    ./easyrsa --batch --req-cn=$NAME gen-req $NAME nopass
    ./easyrsa --batch sign-req server $NAME
    cd -

    ln -s $EASYRSA_DIR/pki/issued/$NAME.crt $CERT_DIR/$NAME.crt
    ln -s $EASYRSA_DIR/pki/private/$NAME.key $CERT_DIR/$NAME.key
    a2ensite $2
}


del_cert_links()
{
    rm -f $CERT_DIR/$1.crt
    rm -f $CERT_DIR/$1.key
}


mkdir -p $CERT_DIR

if [ $USE_LETSENCRYPT = "true" ]
then
    init_le
else
    init_easyrsa
fi
