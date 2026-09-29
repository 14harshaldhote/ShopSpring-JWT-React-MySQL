# syntax=docker/dockerfile:1
# Local OpenID Connect provider for the demo. Not for production.
ARG REGISTRY=docker.io/library

FROM ${REGISTRY}/maven:3-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package && cp target/dev-idp-*.jar /app.jar

FROM ${REGISTRY}/eclipse-temurin:25-jre
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /app.jar app.jar
USER 10001:10001
EXPOSE 9000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
