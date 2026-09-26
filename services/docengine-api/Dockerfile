FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /build
COPY pom.xml .
COPY services/docengine-common/pom.xml services/docengine-common/pom.xml
COPY services/docengine-api/pom.xml services/docengine-api/pom.xml
COPY services/docengine-worker/pom.xml services/docengine-worker/pom.xml
COPY services/docengine-common/src services/docengine-common/src
COPY services/docengine-api/src services/docengine-api/src

RUN mvn -pl services/docengine-api -am package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /build/services/docengine-api/target/docengine-api-0.1.0-SNAPSHOT.jar app.jar
COPY database/migrations database/migrations

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
