# Read-only seed for the local Maven repository. CI passes the Maven cache of its verify job as build context
# `maven-repository` (--build-context), so the image build downloads nothing from Maven Central (HTTP 429 when
# parallel builds each fetch the full dependency set). Without it this empty stage is used.
FROM scratch AS maven-repository

FROM --platform=$BUILDPLATFORM maven:3.10-eclipse-temurin-25@sha256:84bd96155bf37d1be2858433aee89320f487c2d21faf06406640e7dc2b82d686 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=bind,from=maven-repository,target=/maven-repository \
    --mount=type=cache,target=/root/.m2/repository \
    mvn -pl services/embabel-agent-service -am package -DskipTests --no-transfer-progress -Dmaven.repo.local.tail=/maven-repository

FROM eclipse-temurin:25-jre@sha256:4ed132f6832e9804b123e89a849a7b4502756ae64a4ee08ebab939d9d5d0a9fa
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
# HTTPS when a PEM certificate and key are mounted at /etc/openjcockpit/tls/tls.crt and tls.key, plain
# HTTP otherwise (services/docker/entrypoint.sh).
COPY --chmod=0755 services/docker/entrypoint.sh services/docker/healthcheck.sh /usr/local/bin/
USER 10001
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD ["/usr/local/bin/healthcheck.sh", "8081"]
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
