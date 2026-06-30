# Stage 1: compile modified GwtDeviceServiceImpl.java and patch the device JAR.
# Both steps happen here so the JDK's "jar" tool is available.
FROM eclipse-temurin:8-jdk AS compiler

# Pull the entire WEB-INF/lib from the base image as compilation classpath
COPY --from=kapua/kapua-console:1.6.12 \
     /var/opt/jetty/webapps/root/WEB-INF/lib /classpath
# Servlet API is provided by Jetty at runtime (not in WEB-INF/lib) — add explicitly
COPY --from=kapua/kapua-console:1.6.12 \
     /opt/jetty/lib/servlet-api-3.1.jar /classpath/servlet-api-3.1.jar

# Place source in the correct package directory tree (required by javac)
WORKDIR /src
COPY GwtDeviceServiceImpl.java \
     org/eclipse/kapua/app/console/module/device/server/GwtDeviceServiceImpl.java

# Compile
RUN javac -encoding UTF-8 -cp "/classpath/*" \
    org/eclipse/kapua/app/console/module/device/server/GwtDeviceServiceImpl.java

# Use the pre-patched JAR (contains GwtDeviceManagementServiceImpl with ESF fallback)
# as the base, then inject only the freshly compiled GwtDeviceServiceImpl*.class
COPY kapua-console-module-device-1.6.12.jar /tmp/device-patched.jar
RUN find . -name 'GwtDeviceServiceImpl*.class' | sed 's|^\./||' | \
    xargs jar uf /tmp/device-patched.jar

# Stage 2: final image — copy patched JAR and static web files
FROM kapua/kapua-console:1.6.12

USER 0

COPY --chown=1000:0 admin/ \
     /var/opt/jetty/webapps/root/

COPY --from=compiler --chown=1000:0 /tmp/device-patched.jar \
     /var/opt/jetty/webapps/root/WEB-INF/lib/kapua-console-module-device-1.6.12.jar

USER 1000
