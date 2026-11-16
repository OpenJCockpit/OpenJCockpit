FROM maven:3.10-eclipse-temurin-25@sha256:a4734fd7909a9c2beddcacfa1229bd308a349f000bea1ec8db3287c8a16ca2a3 AS build
WORKDIR /workspace
COPY . .
# Reactor build of the runtime and what it needs (service-common) only; the management plane is not built.
RUN mvn -pl services/embabel-agent-runtime -am package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jre@sha256:a640968ae55027b322cedd5f8228b01a84baff0b9e9360e47601c6a111ba1699
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
