FROM kapua/kapua-api:1.6.12

USER 0

# Fix JaxbContextResolver (recompiled with DeviceTokenGenGatewayconfig dependency)
COPY JaxbContextResolver.class \
     /var/opt/jetty/webapps/root/WEB-INF/classes/org/eclipse/kapua/app/api/web/JaxbContextResolver.class

# Add DeviceTokenGenGatewayconfig class (required by JaxbContextResolver)
COPY kapua-gatewayconfig-custom.jar \
     /var/opt/jetty/webapps/root/WEB-INF/lib/kapua-gatewayconfig-custom.jar

# Deploy custom REST API resources (with DeviceNoAuth and registration token APIs)
COPY kapua-api-resources-running.jar \
     /var/opt/jetty/webapps/root/WEB-INF/lib/kapua-rest-api-resources-1.6.12.jar

# DeviceNoAuth provisions broker:connect permission for accounts that predate broker-user creation.
COPY kapua-broker-api-1.6.12.jar \
     /var/opt/jetty/webapps/root/WEB-INF/lib/kapua-broker-api-1.6.12.jar

COPY device-test.html /var/opt/jetty/webapps/root/device-test.html

RUN chown -R 1000:0 /var/opt/jetty/webapps/root/WEB-INF && \
    chown 1000:0 /var/opt/jetty/webapps/root/device-test.html

USER 1000
