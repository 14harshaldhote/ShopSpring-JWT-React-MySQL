# syntax=docker/dockerfile:1
# Multi-stage build: the runtime image has only a JRE and the app, runs as a non-root user,
# and contains no build tools or source.                                   [OWASP A02:2025, A03:2025]
ARG REGISTRY=docker.io/library

FROM ${REGISTRY}/maven:3-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package && cp target/shopeefy-server-*.jar /app.jar

FROM ${REGISTRY}/eclipse-temurin:25-jre
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /app.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
