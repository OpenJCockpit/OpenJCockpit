FROM maven:3.9-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS build
WORKDIR /workspace
COPY . .
# Reactor build of the runtime and what it needs (service-common) only; the management plane is not built.
RUN mvn -pl services/embabel-agent-runtime -am package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jre@sha256:dbb43d81099eec91e0800226ed2296d03146ee7857165b95112372ad0d67e87f
WORKDIR /app
# Unprivileged user; the only writable places are /tmp (per-run skill directories) and the optional
# last-known-good cache directory an operator mounts.
RUN useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin runtime
COPY --from=build /workspace/services/embabel-agent-runtime/target/*.jar app.jar
# The seeded configuration-driven agents ship with the image, so the runtime needs no shared filesystem;
# a deployment can override them with a read-only mount at /agent-definitions.
COPY --from=build /workspace/services/embabel-agent-runtime/agent-definitions /agent-definitions
USER 10001
EXPOSE 8082
ENV AGENT_DEFINITIONS_LOCAL_ROOT=/agent-definitions
# Readiness is the runtime's own (no database); dependencies are checked per request, failing closed.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8082 && printf 'GET /actuator/health/readiness HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
