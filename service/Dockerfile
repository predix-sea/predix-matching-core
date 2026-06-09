FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY service/pom.xml .
COPY service/docker/maven-settings.xml /root/.m2/settings.xml
COPY core/proto /core/proto
RUN mvn -B -q -DskipTests dependency:go-offline
COPY service/src ./src
RUN mvn -B -q -DskipTests package && mv target/*.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
