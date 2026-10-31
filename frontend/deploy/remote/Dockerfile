# One image per remote. Build context is the frontend/ folder:
#   docker build -f deploy/remote/Dockerfile --build-arg REMOTE=remote-skills-hub -t openjcockpit-remote-skills-hub frontend
FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS manifests
WORKDIR /src
COPY apps apps
COPY libs libs
COPY e2e e2e
RUN mkdir /manifests \
 && find apps libs e2e -name package.json -not -path '*/node_modules/*' | tar -cf - -T - | tar -xf - -C /manifests

FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS build
ARG REMOTE
RUN test -n "$REMOTE" || { echo "pass --build-arg REMOTE=<apps/ folder name>" >&2; exit 1; }
WORKDIR /app
COPY package.json package-lock.json nx.json tsconfig.base.json vite.config.base.ts module-federation.shared.ts ./
COPY --from=manifests /manifests ./
# All workspaces: the shared singletons (api, i18n, ui, hub-contract) must resolve even where this remote does not import them.
RUN --mount=type=cache,target=/root/.npm npm ci
COPY libs libs
COPY apps/$REMOTE apps/$REMOTE
RUN NX_DAEMON=false npx nx run "$REMOTE":build

FROM nginxinc/nginx-unprivileged:1.27-alpine@sha256:65e3e85dbaed8ba248841d9d58a899b6197106c23cb0ff1a132b7bfe0547e4c0
ARG REMOTE
ARG BUILD_SHA=unknown
# Origin of the host that loads this remote; the only origin CORS lets read its files.
ENV BUILD_SHA=$BUILD_SHA \
    HOST_ORIGIN=http://localhost:4000 \
    NGINX_ENVSUBST_FILTER=^(HOST_ORIGIN$|BUILD_SHA$)
COPY --from=build /app/apps/$REMOTE/dist /usr/share/nginx/html
COPY deploy/remote/nginx.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
