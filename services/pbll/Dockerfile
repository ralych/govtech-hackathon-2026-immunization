# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --quiet fatJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/pbll-0.1.0-all.jar /app/app.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xms64m -Xmx256m -XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
