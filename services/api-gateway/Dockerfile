FROM maven:3.9.16-eclipse-temurin-21-alpine AS builder

WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=builder --chown=app:app /build/target/api-gateway-0.0.1-SNAPSHOT.jar /app/app.jar

USER app
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
