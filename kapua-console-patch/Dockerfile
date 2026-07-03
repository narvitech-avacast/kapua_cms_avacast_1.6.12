# Stage 1: compile patched Java files and inject into JARs.
FROM eclipse-temurin:8-jdk AS compiler

# Pull the entire WEB-INF/lib from the base image as compilation classpath
COPY --from=kapua/kapua-console:1.6.12 \
     /var/opt/jetty/webapps/root/WEB-INF/lib /classpath
# Servlet API is provided by Jetty at runtime (not in WEB-INF/lib) — add explicitly
COPY --from=kapua/kapua-console:1.6.12 \
     /opt/jetty/lib/servlet-api-3.1.jar /classpath/servlet-api-3.1.jar

WORKDIR /src

# --- Patch 1: kapua-console-module-device JAR (GwtDeviceServiceImpl, GwtDeviceManagementServiceImpl) ---
COPY GwtDeviceServiceImpl.java \
     org/eclipse/kapua/app/console/module/device/server/GwtDeviceServiceImpl.java
COPY GwtDeviceManagementServiceImpl.java \
     org/eclipse/kapua/app/console/module/device/server/GwtDeviceManagementServiceImpl.java

RUN javac -encoding UTF-8 -cp "/classpath/*" \
    org/eclipse/kapua/app/console/module/device/server/GwtDeviceServiceImpl.java \
    org/eclipse/kapua/app/console/module/device/server/GwtDeviceManagementServiceImpl.java

COPY kapua-console-module-device-1.6.12.jar /tmp/device-patched.jar
RUN (find . -name 'GwtDeviceServiceImpl*.class'; find . -name 'GwtDeviceManagementServiceImpl*.class') \
    | sed 's|^\./||' | xargs jar uf /tmp/device-patched.jar

# --- Patch 2: kapua-commons JAR (TscalarImpl — tolerate unknown AD types like "Action") ---
COPY commons-patch/TscalarImpl.java \
     org/eclipse/kapua/commons/configuration/metatype/TscalarImpl.java

RUN javac -encoding UTF-8 -cp "/classpath/*" \
    org/eclipse/kapua/commons/configuration/metatype/TscalarImpl.java

COPY --from=kapua/kapua-console:1.6.12 \
     /var/opt/jetty/webapps/root/WEB-INF/lib/kapua-commons-1.6.12.jar /tmp/commons-patched.jar
RUN jar uf /tmp/commons-patched.jar \
    org/eclipse/kapua/commons/configuration/metatype/TscalarImpl.class

# Stage 2: final image — copy patched JARs and static web files
FROM kapua/kapua-console:1.6.12

USER 0

COPY --chown=1000:0 admin/ \
     /var/opt/jetty/webapps/root/

COPY --from=compiler --chown=1000:0 /tmp/device-patched.jar \
     /var/opt/jetty/webapps/root/WEB-INF/lib/kapua-console-module-device-1.6.12.jar

COPY --from=compiler --chown=1000:0 /tmp/commons-patched.jar \
     /var/opt/jetty/webapps/root/WEB-INF/lib/kapua-commons-1.6.12.jar

USER 1000
