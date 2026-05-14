# syntax=docker/dockerfile:1.7
#
# CH VACD API Reference Server — multi-stage build.
# Built and run twice in docker-compose.yml as fhir-producer / fhir-consumer.
#
# Default listen port is 9111 (from src/main/resources/application.yml).
# Override with SERVER_PORT env at runtime for the consumer instance.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -e -DskipTests package \
 && mv target/ch-vacd-api-reference-server-*.jar target/app.jar


FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN useradd --create-home --shell /bin/sh --uid 1001 spring
USER spring

COPY --from=build --chown=spring:spring /workspace/target/app.jar /app/app.jar

EXPOSE 9111
ENTRYPOINT ["java","-jar","/app/app.jar"]
