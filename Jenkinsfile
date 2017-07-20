@Library('libpipelines@master') _

hose {
    EMAIL = 'sparta'
    MODULE = 'sparta-workflow'
    DEVTIMEOUT = 70
    RELEASETIMEOUT = 40
    FOSS = true
    REPOSITORY = 'sparta-workflow'
    PKGMODULES = ['dist']
    PKGMODULESNAMES = ['stratio-sparta']
    DEBARCH = 'all'
    RPMARCH = 'noarch'
    EXPOSED_PORTS = [9090]
    KMS_UTILS = '0.2.1'
    BASEIMG = 'qa.stratio.com/stratio/stratio-spark:2.1.0.1'
    DOCKERFILECOMMAND = 'WORKDIR / \n RUN apt-get update -y && apt-get install -y krb5-user libpam-krb5 libpam-ccreds auth-client-config curl wget php5-curl make jq vim && update-alternatives --set java /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java && curl https://www.openssl.org/source/openssl-1.0.2l.tar.gz | tar xz && cd openssl-1.0.2l && sudo ./config && sudo make && sudo make install && sudo ln -sf /usr/local/ssl/bin/openssl /usr/bin/openssl && wget https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64 && chmod +x jq-linux64 && mv jq-linux64 /usr/bin/jq'

    ITSERVICES = [
            ['RABBITMQ': [
               'image': 'rabbitmq:3-management'
            ]],
          ]

    ITPARAMETERS = "-Drabbitmq.hosts=%%RABBITMQ"


    DEV = { config ->
    
        doCompile(config)

        parallel(UT: {
            doUT(config)
        }, IT: {
            doIT(config)
        }, failFast: config.FAILFAST)

        doPackage(config)

        parallel(DOC: {
            doDoc(config)
        }, DEPLOY: {
            doDeploy(config)
        }, DOCKER : {    
            doDocker(config)
        }, failFast: config.FAILFAST)

    }
}
