FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY . .

ARG SKIP_TESTS=false
RUN mvn -B -ntp clean verify -DskipTests=${SKIP_TESTS}

FROM eclipse-temurin:21-jre-jammy AS service

WORKDIR /app

COPY --from=build /app/target/contact-service-0.0.1-SNAPSHOT.jar contact-service.jar
USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "contact-service.jar"]
