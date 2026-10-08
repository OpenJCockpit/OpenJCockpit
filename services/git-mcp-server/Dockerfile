FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl services/git-mcp-server -am package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/services/git-mcp-server/target/*.jar app.jar
EXPOSE 8093
ENTRYPOINT ["java", "-jar", "app.jar"]
