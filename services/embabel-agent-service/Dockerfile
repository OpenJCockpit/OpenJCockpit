FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl services/embabel-agent-service -am package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/services/embabel-agent-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
