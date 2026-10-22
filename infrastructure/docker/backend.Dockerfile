# =====================================================================================
# Backend image — multi-stage so the build toolchain and the Maven cache never reach runtime.
#
#   docker build -f infrastructure/docker/backend.Dockerfile -t realtime-chat-backend backend
#
# Stage 1 resolves dependencies against the POM only. That layer is cached and only invalidated when
# pom.xml changes, which is the difference between a 20-second image build and a five-minute one.
# =====================================================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies separately from sources.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
# Tests are not run here: they need Docker (Testcontainers) and belong in CI where that is available.
# See docs/testing-strategy.md.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q clean package -DskipTests

# -------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Run unprivileged. A chat server holds session secrets; it has no reason to be root.
RUN addgroup -S app && adduser -S -G app -h /app app
WORKDIR /app

# curl is used by the container health check in docker-compose.yml.
RUN apk add --no-cache curl tzdata

COPY --from=build --chown=app:app /workspace/target/*.jar /app/app.jar

USER app
EXPOSE 8080

# Container-aware heap: 75% of the cgroup limit, so the JVM sizes itself to the container rather than
# to the host. Exits on OOM instead of limping along with a corrupt heap.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
