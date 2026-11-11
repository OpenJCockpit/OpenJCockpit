FROM maven:3.10-eclipse-temurin-25@sha256:047a82fc82c613266cfb1b66fd920be9c8ba51fd67edc68245e4542768f45e36 AS build
WORKDIR /workspace
COPY . .
# Reactor build of the runtime and what it needs (service-common) only; the management plane is not built.
RUN mvn -pl services/embabel-agent-runtime -am package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jre@sha256:ecdc0adb37bde1a281c4d245a3cd535004b1fc8322d560f5b09463b248c81df0
WORKDIR /app
# Unprivileged user; the only writable places are /tmp (per-run skill directories) and the optional
# last-known-good cache directory an operator mounts.
RUN useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin runtime
COPY --from=build /workspace/services/embabel-agent-runtime/target/*.jar app.jar
# The seeded configuration-driven agents ship with the image, so the runtime needs no shared filesystem;
# a deployment can override them with a read-only mount at /agent-definitions.
COPY --from=build /workspace/services/embabel-agent-runtime/agent-definitions /agent-definitions
# HTTPS when a PEM certificate and key are mounted at /etc/openjcockpit/tls/tls.crt and tls.key, plain
# HTTP otherwise (services/docker/entrypoint.sh).
COPY --chmod=0755 services/docker/entrypoint.sh services/docker/healthcheck.sh /usr/local/bin/
USER 10001
EXPOSE 8082
ENV AGENT_DEFINITIONS_LOCAL_ROOT=/agent-definitions
# Readiness is the runtime's own (no database); dependencies are checked per request, failing closed.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD ["/usr/local/bin/healthcheck.sh", "8082"]
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
