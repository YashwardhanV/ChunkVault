FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S chunkvault && adduser -S chunkvault -G chunkvault \
    && mkdir -p /data/chunks /tmp/chunkvault-multipart \
    && chown -R chunkvault:chunkvault /data /tmp/chunkvault-multipart
USER chunkvault
WORKDIR /app
COPY --from=build /build/target/chunkvault-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
