FROM gradle:8.5-jdk17 AS build

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src

RUN gradle buildFatJar --no-daemon -x test

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=build /app/build/libs/app.jar /app/app.jar
COPY --from=build /app/src/main/resources/docker /app/docker-templates

ENV APP_PORT=8080 \
    DOCKER_TEMPLATES_DIR=/app/docker-templates

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
