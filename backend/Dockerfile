# syntax=docker/dockerfile:1.7
# Build context: repository root (the backend depends on the solver module).
#   docker build -f backend/Dockerfile -t examgrid-api .

FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /src
# Poms first so dependency resolution is cached until a pom changes.
COPY pom.xml .
COPY solver/pom.xml solver/
COPY benchmarks/pom.xml benchmarks/
COPY backend/pom.xml backend/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl backend -am dependency:go-offline -DskipTests
COPY solver/src solver/src
COPY backend/src backend/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl backend -am package -DskipTests \
 && java -Djarmode=tools -jar backend/target/examgrid-backend.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S examgrid && adduser -S -G examgrid examgrid \
 && mkdir -p /data/storage && chown -R examgrid:examgrid /data
WORKDIR /app
# Spring Boot layers, least to most frequently changing, so rebuilds reuse cached layers.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER examgrid
ENV SPRING_PROFILES_ACTIVE=prod \
    EXAMGRID_STORAGE_ROOT=/data/storage \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true"
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
