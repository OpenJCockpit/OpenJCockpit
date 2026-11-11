FROM maven:3.10-eclipse-temurin-25@sha256:047a82fc82c613266cfb1b66fd920be9c8ba51fd67edc68245e4542768f45e36 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl services/ai-control-service -am package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jre@sha256:ecdc0adb37bde1a281c4d245a3cd535004b1fc8322d560f5b09463b248c81df0
WORKDIR /app
# Unprivileged user (same UID as embabel-agent-runtime). The only writable places are /tmp and the
# data directories below, which are created here so fresh volumes inherit the ownership.
RUN useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin app \
 && mkdir -p /workflow-workspaces \
 && chown 10001:10001 /workflow-workspaces
COPY --from=build /workspace/services/ai-control-service/target/*.jar app.jar
# HTTPS when a PEM certificate and key are mounted at /etc/openjcockpit/tls/tls.crt and tls.key, plain
# HTTP otherwise (services/docker/entrypoint.sh).
COPY --chmod=0755 services/docker/entrypoint.sh services/docker/healthcheck.sh /usr/local/bin/
USER 10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD ["/usr/local/bin/healthcheck.sh", "8080"]
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
