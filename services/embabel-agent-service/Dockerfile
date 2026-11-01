FROM maven:3.9-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl services/embabel-agent-service -am package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jre@sha256:fcd7fd7b387f94bb2ac461478a7436ad8e349924c374ea8313919624dceae636
WORKDIR /app
# Unprivileged user (same UID as embabel-agent-runtime). The only writable places are /tmp and the
# data directories below, which are created here so fresh volumes inherit the ownership.
RUN useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin app \
 && mkdir -p /workflow-workspaces /workflow-definitions /tmp/spec-workflow \
 && chown 10001:10001 /workflow-workspaces /workflow-definitions /tmp/spec-workflow
COPY --from=build /workspace/services/embabel-agent-service/target/*.jar app.jar
# The agent catalog ships with the image (read-only); the local stack mounts ./agents over it.
COPY --from=build /workspace/agents /agents
ENV AGENT_CATALOG_LOCAL_PATH=/agents
# Spec-workflow scaffolding (templates/, specs/, AGENTS.md) is written at startup; /app is read-only for
# the app user. The initializer recreates the folder when /tmp is a tmpfs/emptyDir mount.
ENV SPEC_WORKFLOW_BASE=/tmp/spec-workflow
USER 10001
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${MANAGEMENT_PORT:-8081} && printf 'GET /actuator/health/readiness HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
