# syntax=docker/dockerfile:1.7

# ===== Build stage =====
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY .mvn ./.mvn
COPY mvnw ./
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests -Dmaven.wagon.http.retryHandler.count=3 package

# ===== Run stage (léger) =====
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar
ARG SERVER_PORT=8090
ENV SERVER_PORT=${SERVER_PORT}
EXPOSE ${SERVER_PORT}
ENTRYPOINT ["java","-jar","/app/app.jar"]
