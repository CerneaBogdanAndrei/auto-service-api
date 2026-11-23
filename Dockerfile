# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build stage: compile and package with the Maven Wrapper on JDK 21
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# The Maven Wrapper falls back to the .tar.gz distribution when unzip is missing,
# which would not match the pinned .zip checksum in maven-wrapper.properties.
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

# Resolve dependencies in a separate layer so it is reused while only sources change.
COPY .mvn/ .mvn/
COPY --chmod=0755 mvnw ./
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp dependency:go-offline

# Tests are not run here: integration tests need Docker (Testcontainers) and run in CI.
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp package -DskipTests \
    && cp target/auto-service-api-*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------------------------------------------------------------------------
# Runtime stage: slim JRE, non-root user, layered application
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S app && adduser -S -G app app
WORKDIR /application

# Least-frequently-changing layers first for better image layer caching.
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER app
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar application.jar"]
