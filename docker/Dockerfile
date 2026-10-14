# One Dockerfile for every CareerPilot module.
# Stage 1 builds all boot jars once (Docker reuses this stage for every service);
# stage 2 copies the jar of the module named by MODULE_DIR.
# RUNTIME_IMAGE / EXTRA_PACKAGES let the submission service add compilers for the local code runner.

ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-jammy

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src
COPY gradlew gradlew.bat settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --version --no-daemon
COPY common common
COPY services services
COPY web-ui web-ui
RUN ./gradlew bootJar -x test --no-daemon --parallel

FROM ${RUNTIME_IMAGE}
ARG MODULE_DIR
ARG EXTRA_PACKAGES=""
# curl for health checks; fonts for Java2D (captcha images, PDF reports)
RUN apt-get update && apt-get install -y --no-install-recommends curl fontconfig fonts-dejavu-core ${EXTRA_PACKAGES} \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --uid 10001 placify
WORKDIR /app
COPY --from=build /src/${MODULE_DIR}/build/libs/*.jar /app/app.jar
RUN mkdir -p /app/storage /home/placify/.djl.ai && chown -R placify:placify /app /home/placify
USER placify
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC" \
    STORAGE_ROOT=/app/storage \
    DJL_CACHE_DIR=/home/placify/.djl.ai
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
