FROM kapua/kapua-broker:1.6.12

USER 0

COPY kapua-device-registry-internal-1.6.12.jar \
     /opt/activemq/lib/extra/kapua-device-registry-internal-1.6.12.jar

COPY kapua-broker-core-1.6.12.jar \
     /opt/activemq/lib/extra/kapua-broker-core-1.6.12.jar

RUN chown 1002:0 \
    /opt/activemq/lib/extra/kapua-device-registry-internal-1.6.12.jar \
    /opt/activemq/lib/extra/kapua-broker-core-1.6.12.jar

USER 1002
