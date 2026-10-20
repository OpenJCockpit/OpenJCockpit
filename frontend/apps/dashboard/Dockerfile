# Build context is the frontend/ folder (see docker-compose.yml): the dashboard is an Nx workspace project.
FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS build
WORKDIR /app
COPY package.json package-lock.json nx.json tsconfig.base.json ./
COPY apps/dashboard/package.json apps/dashboard/
COPY libs/api/package.json libs/api/
COPY libs/auth/package.json libs/auth/
COPY libs/landing-hub/package.json libs/landing-hub/
COPY libs/project/package.json libs/project/
COPY libs/ui/package.json libs/ui/
COPY e2e/package.json e2e/
RUN npm ci --workspace=openjcockpit-frontend --include-workspace-root
COPY libs libs
COPY apps/dashboard apps/dashboard
RUN NX_DAEMON=false npx nx run dashboard:build

FROM nginx:1.27-alpine@sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10
COPY --from=build /app/apps/dashboard/dist /usr/share/nginx/html
COPY apps/dashboard/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
