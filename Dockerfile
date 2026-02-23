FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests

FROM gcr.io/distroless/java21-debian12:nonroot AS service

WORKDIR /app

COPY --from=build /app/target/contact-service-0.0.1-SNAPSHOT.jar contact-service.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "contact-service.jar"]