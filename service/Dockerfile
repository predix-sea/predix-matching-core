FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn -q -DskipTests package && mv target/*.jar app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/app.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
