FROM maven:3.9-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl services/git-mcp-server -am package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jre@sha256:fcd7fd7b387f94bb2ac461478a7436ad8e349924c374ea8313919624dceae636
WORKDIR /app
# Unprivileged user (same UID as embabel-agent-runtime). The only writable places are /tmp and the
# data directories below, which are created here so fresh volumes inherit the ownership.
RUN useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin app \
 && mkdir -p /git-mcp-workspaces \
 && chown 10001:10001 /git-mcp-workspaces
COPY --from=build /workspace/services/git-mcp-server/target/*.jar app.jar
USER 10001
EXPOSE 8093
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${MANAGEMENT_PORT:-8093} && printf 'GET /actuator/health/readiness HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
