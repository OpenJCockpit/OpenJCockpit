# Build context is the frontend/ folder (see docker-compose.yml): the dashboard is an Nx workspace project.
FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS manifests
# Collect every workspace package.json so a new lib needs no Dockerfile edit. This stage reruns on any
# source change, but its output layer is content-addressed, so the npm ci layer below stays cached
# until a manifest actually changes.
WORKDIR /src
COPY apps apps
COPY libs libs
COPY e2e e2e
RUN mkdir /manifests \
 && find apps libs e2e -name package.json -not -path '*/node_modules/*' | tar -cf - -T - | tar -xf - -C /manifests

FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS build
WORKDIR /app
COPY package.json package-lock.json nx.json tsconfig.base.json vite.config.base.ts module-federation.shared.ts ./
COPY --from=manifests /manifests ./
RUN --mount=type=cache,target=/root/.npm npm ci --workspace=openjcockpit-frontend --include-workspace-root
COPY libs libs
COPY apps/dashboard apps/dashboard
RUN NX_DAEMON=false npx nx run dashboard:build

FROM nginxinc/nginx-unprivileged:1.27-alpine@sha256:65e3e85dbaed8ba248841d9d58a899b6197106c23cb0ff1a132b7bfe0547e4c0
# The nginx image renders /etc/nginx/templates/*.template into /etc/nginx/conf.d at startup. The filter
# keeps envsubst from touching nginx's own $variables ($uri, $host, ...).
ARG BUILD_SHA=unknown
ENV BUILD_SHA=$BUILD_SHA \
    API_UPSTREAM=http://ai-control-service:8080 \
    KEYCLOAK_URL=http://localhost:8080 \
    KEYCLOAK_REALM=openjcockpit \
    KEYCLOAK_CLIENT_ID=openjcockpit \
    FEDERATION_MANIFEST={} \
    FEDERATION_ALLOWED_ORIGINS_JSON=[] \
    FEDERATION_ALLOWED_ORIGINS= \
    NGINX_ENVSUBST_FILTER=^(KEYCLOAK_|FEDERATION_|API_UPSTREAM$|BUILD_SHA$)
COPY --from=build /app/apps/dashboard/dist /usr/share/nginx/html
COPY apps/dashboard/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY apps/dashboard/security-headers.inc.template /etc/nginx/templates/security-headers.inc.template
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
