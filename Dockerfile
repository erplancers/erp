# Build with the same Java version as the project's Java 25 toolchain.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy Gradle configuration early so Docker uses the repository's JVM/worker limits.
COPY gradlew gradlew
COPY gradle gradle
COPY gradle.properties build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon

# Match the runtime to the Java version used to compile the application.
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 erp
COPY --from=build /app/build/libs/*.jar /app/app.jar
RUN chown -R erp:erp /app
USER erp
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
