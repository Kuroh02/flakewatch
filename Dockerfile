# syntax=docker/dockerfile:1

# Stage 1: Build frontend
FROM node:24-alpine AS web-builder
WORKDIR /app/web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

# Stage 2: Build server
FROM eclipse-temurin:25-jdk AS server-builder
WORKDIR /app

# Copy Maven files
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY core/pom.xml core/pom.xml
COPY server/pom.xml server/pom.xml

# Cache dependencies
RUN ./mvnw dependency:go-offline -B -pl server -am || true

# Copy source code
COPY core/src core/src
COPY server/src server/src

# Copy built frontend assets into server static resources
COPY --from=web-builder /app/web/dist server/src/main/resources/static/

# Package application layered jar
RUN ./mvnw -pl server -am package -DskipTests -B

# Extract layered jar
WORKDIR /app/server/target
RUN java -Djarmode=tools -jar server-*.jar extract --layers --destination /app/extracted

# Stage 3: Runtime image
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Install curl for HEALTHCHECK and create non-root user
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    useradd -u 10001 -r -s /bin/false flakewatch && \
    chown -R flakewatch:flakewatch /app

USER flakewatch:flakewatch

# Copy layered jar contents
COPY --from=server-builder --chown=flakewatch:flakewatch /app/extracted/dependencies/ ./
COPY --from=server-builder --chown=flakewatch:flakewatch /app/extracted/spring-boot-loader/ ./
COPY --from=server-builder --chown=flakewatch:flakewatch /app/extracted/snapshot-dependencies/ ./
COPY --from=server-builder --chown=flakewatch:flakewatch /app/extracted/application/ ./

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=128m"

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
