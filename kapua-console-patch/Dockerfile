FROM kapua/kapua-console:1.6.12

USER 0

COPY --chown=1000:0 admin/ \
     /var/opt/jetty/webapps/root/

COPY kapua-console-module-device-1.6.12.jar \
     /var/opt/jetty/webapps/root/WEB-INF/lib/kapua-console-module-device-1.6.12.jar

RUN chown 1000:0 \
    /var/opt/jetty/webapps/root/WEB-INF/lib/kapua-console-module-device-1.6.12.jar

USER 1000
