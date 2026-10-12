# Build stage - Temurin JDK 25 + pinned Maven wrapper (see .mvn/wrapper)
FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /app

# Wrapper first for dependency caching; chmod because git on Windows drops +x
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Source code, then build (tests run in CI/local suites, not in the image)
COPY src src
RUN ./mvnw clean package -DskipTests -B

# Runtime stage - Temurin JRE 25, non-root
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# wget is the healthcheck probe; non-root user per deployment strategy
RUN apk add --no-cache wget && \
    addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

COPY --from=build /app/target/echo-backend-1.0.0-SNAPSHOT.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

# Sized for small PaaS instances; override with JAVA_OPTS env in render.yaml
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

# PORT is injected by Render; default keeps local `docker run` working.
# Probes /api/health (public, no auth) rather than /actuator (locked down).
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:${PORT:-8080}/api/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
