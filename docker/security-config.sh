#!/bin/bash

function _log_sparta_sec() {
    local message=$1
    echo "[SPARTA-SECURITY] $message"
}

_log_sparta_sec "Loading kms-utils ... "
source /kms_utils.sh
_log_sparta_sec "Loaded kms-utils"

if [ "$USE_DYNAMIC_AUTHENTICATION" = "true" ]; then
    _log_sparta_sec "Dynamic authentication enabled. Obtaining token from Vault"
    login
    if [ $? != 0 ]; then
        _log_sparta_sec "ERROR" "login using dynamic authentication failed!"
        exit 1
    fi

#   TODO prevent error in Spark
#    if [ -v VAULT_ROLE_ID ]; then
#      echo "" >> ${VARIABLES}
#      echo "unset VAULT_ROLE_ID" >> ${VARIABLES}
#      echo "" >> ${SYSTEM_VARIABLES}
#      echo "unset VAULT_ROLE_ID" >> ${SYSTEM_VARIABLES}
#    fi
#
#    if [ -v VAULT_SECRET_ID ]; then
#      echo "unset VAULT_SECRET_ID" >> ${VARIABLES}
#      echo "" >> ${VAULT_SECRET_ID}
#      echo "unset VAULT_ROLE_ID" >> ${SYSTEM_VARIABLES}
#    fi

fi

if [ -v VAULT_ENABLE ] && [ ${#VAULT_ENABLE} != 0 ] && [ $VAULT_ENABLE == "true" ] && [ -v VAULT_TOKEN ] && [ ${#VAULT_TOKEN} != 0 ]; then
  echo "" >> ${VARIABLES}
  echo "export VAULT_TOKEN=$VAULT_TOKEN" >> ${VARIABLES}
  echo "" >> ${SYSTEM_VARIABLES}
  echo "export VAULT_TOKEN=$VAULT_TOKEN" >> ${SYSTEM_VARIABLES}

# TODO check if generate errors in Spark
# echo "" >> ${VARIABLES}
# echo "export VAULT_TEMP_TOKEN=$VAULT_TOKEN" >> ${VARIABLES}
# echo "" >> ${SYSTEM_VARIABLES}
# echo "export VAULT_TEMP_TOKEN=$VAULT_TOKEN" >> ${SYSTEM_VARIABLES}

fi

#Ensure security folder is created
mkdir -p /etc/sds/sparta/security

# Main execution

## Init
if [ -v MARATHON_APP_LABEL_DCOS_SERVICE_NAME ] && [ ${#MARATHON_APP_LABEL_DCOS_SERVICE_NAME} != 0 ]; then
export TENANT_NAME=${MARATHON_APP_LABEL_DCOS_SERVICE_NAME}
else
export TENANT_NAME='sparta'   # MARATHON_APP_ID without slash
fi
#Setup tenant_normalized for access kms_utils
export TENANT_UNDERSCORE=${TENANT_NAME//-/_}
export TENANT_NORM="${TENANT_UNDERSCORE^^}"


export SPARTA_TLS_JKS_NAME="/etc/sds/sparta/security/$TENANT_NAME.jks"
export SPARTA_TRUST_JKS_NAME="/etc/sds/sparta/security/truststore.jks"
export SPARTA_KEYTAB_NAME="/etc/sds/sparta/security/$TENANT_NAME.keytab"
export GOSEC_PLUGIN_JKS_NAME=${SPARTA_TLS_JKS_NAME}


####################################################
## Get TLS Server Info and set SPARTA_KEYSTORE_PASS
####################################################
 if [ -v SECURITY_TLS_ENABLE ] && [ ${#SECURITY_TLS_ENABLE} != 0 ] && [ $SECURITY_TLS_ENABLE == "true" ]; then
  _log_sparta_sec "Configuring tls ..."
  source /tls-config.sh
  _log_sparta_sec "Configuring tls Ok"
 fi

#######################################################
## Create Sparta Truststore and set DEFAULT_KEYSTORE_PASS
#######################################################
if [ -v SECURITY_TRUSTSTORE_ENABLE ] && [ ${#SECURITY_TRUSTSTORE_ENABLE} != 0 ] && [ $SECURITY_TRUSTSTORE_ENABLE == "true" ]; then
  _log_sparta_sec "Configuring truststore ..."
  source /truststore-config.sh
  _log_sparta_sec "Configuring truststore OK"
fi

####################################################
## Kerberos config set SPARTA_PRINCIPAL_NAME and SPARTA_KEYTAB_PATH
####################################################
if [ -v SECURITY_KRB_ENABLE ] && [ ${#SECURITY_KRB_ENABLE} != 0 ] && [ $SECURITY_KRB_ENABLE == "true" ]; then
  _log_sparta_sec "Configuring kerberos ..."
  source /kerberos-server-config.sh
fi

#######################################################
## Gosec-plugin config
#######################################################
if [ -v ENABLE_GOSEC_AUTH ] && [ ${#ENABLE_GOSEC_AUTH} != 0 ] && [ $ENABLE_GOSEC_AUTH == "true" ]; then
  _log_sparta_sec "Configuring GoSec Dyplon plugin ..."
    source /gosec-config.sh
  _log_sparta_sec "Configuring GoSec Dyplon plugin Ok"
fi

#######################################################
## Oauth config set OAUTH2_ENABLE OAUTH2_CLIENT_ID OAUTH2_CLIENT_SECRET
#######################################################
if [ -v SECURITY_OAUTH2_ENABLE ] && [ ${#SECURITY_OAUTH2_ENABLE} != 0 ] && [ $SECURITY_OAUTH2_ENABLE == "true" ]; then
  _log_sparta_sec "Configuring Oauth ..."
  source /oauth2.sh
  _log_sparta_sec "Configuring Oauth Ok"
fi

#######################################################
## MesosSecurity config set MESOS_USER and MESOS_PASS
#######################################################
if [ -v SECURITY_MESOS_ENABLE ] && [ ${#SECURITY_MESOS_ENABLE} != 0 ] && [ $SECURITY_MESOS_ENABLE == "true" ]; then
 _log_sparta_sec "Configuring Mesos Security ..."
 source mesos-security.sh
 _log_sparta_sec "Configuring Mesos Security Ok"
fi

#######################################################
## MarathonSecurity config set MARATHON_SSO_USERNAME and MARATHON_SSO_PASSWORD
#######################################################
if [ -v SECURITY_MARATHON_ENABLED ] && [ ${#SECURITY_MARATHON_ENABLED} != 0 ] && [ $SECURITY_MARATHON_ENABLED == "true" ]; then
 _log_sparta_sec "Configuring Marathon Security ..."
 source marathon-sso-security.sh
 _log_sparta_sec "Configuring Marathon Security Ok"
fi