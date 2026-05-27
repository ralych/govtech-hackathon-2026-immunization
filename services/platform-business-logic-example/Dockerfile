# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --quiet fatJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/platform-business-logic-example-0.1.0-all.jar /app/app.jar
COPY --from=examples . /examples
EXPOSE 8888
ENV JAVA_OPTS="-Xms64m -Xmx256m -XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
