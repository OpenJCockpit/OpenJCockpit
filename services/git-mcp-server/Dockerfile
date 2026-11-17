# Read-only seed for the local Maven repository. CI passes the Maven cache of its verify job as build context
# `maven-repository` (--build-context), so the image build downloads nothing from Maven Central (HTTP 429 when
# parallel builds each fetch the full dependency set). Without it this empty stage is used.
FROM scratch AS maven-repository

FROM maven:3.10-eclipse-temurin-25@sha256:a4734fd7909a9c2beddcacfa1229bd308a349f000bea1ec8db3287c8a16ca2a3 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=bind,from=maven-repository,target=/maven-repository \
    --mount=type=cache,target=/root/.m2/repository \
    mvn -pl services/git-mcp-server -am package -DskipTests --no-transfer-progress -Dmaven.repo.local.tail=/maven-repository

FROM eclipse-temurin:25-jre@sha256:a640968ae55027b322cedd5f8228b01a84baff0b9e9360e47601c6a111ba1699
WORKDIR /app
# Unprivileged user (same UID as embabel-agent-runtime). The only writable places are /tmp and the
# data directories below, which are created here so fresh volumes inherit the ownership.
RUN useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin app \
 && mkdir -p /git-mcp-workspaces \
 && chown 10001:10001 /git-mcp-workspaces
COPY --from=build /workspace/services/git-mcp-server/target/*.jar app.jar
# HTTPS when a PEM certificate and key are mounted at /etc/openjcockpit/tls/tls.crt and tls.key, plain
# HTTP otherwise (services/docker/entrypoint.sh).
COPY --chmod=0755 services/docker/entrypoint.sh services/docker/healthcheck.sh /usr/local/bin/
USER 10001
EXPOSE 8093
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD ["/usr/local/bin/healthcheck.sh", "8093"]
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
