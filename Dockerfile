# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /build

# Copy parent POM and all module POMs first — allows Docker layer caching
# of Maven dependency downloads when only source code changes
COPY pom.xml .
COPY carpool-common/pom.xml   carpool-common/
COPY carpool-domain/pom.xml   carpool-domain/
COPY carpool-repository/pom.xml carpool-repository/
COPY carpool-service/pom.xml  carpool-service/
COPY carpool-bot/pom.xml      carpool-bot/
COPY carpool-web/pom.xml      carpool-web/

# Download dependencies (cached layer — only invalidated when POMs change)
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -q -Dmaven.compiler.forceJavacCompilerUse=true

# Copy source code
COPY carpool-common/src     carpool-common/src
COPY carpool-domain/src     carpool-domain/src
COPY carpool-repository/src carpool-repository/src
COPY carpool-service/src    carpool-service/src
COPY carpool-bot/src        carpool-bot/src
COPY carpool-web/src        carpool-web/src

# Build — skip tests in Docker build (tests run in CI pipeline)
RUN mvn clean package -pl carpool-web -am -DskipTests -q \
    -Dmaven.compiler.forceJavacCompilerUse=true

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
# Use JRE-only alpine image — significantly smaller than JDK
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Non-root user for security
RUN addgroup -S carpool && adduser -S carpool -G carpool
USER carpool

COPY --from=builder /build/carpool-web/target/carpool-web-*.jar app.jar

# Actuator health check port
EXPOSE 8080

# JVM tuning for container environments:
# -XX:+UseContainerSupport        — respects Docker memory limits
# -XX:MaxRAMPercentage=75.0       — use 75% of container RAM for heap
# -Djava.security.egd=...         — faster startup (avoids blocking entropy)
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Duser.timezone=Asia/Manila", \
    "-jar", "app.jar"]
