# syntax=docker/dockerfile:1
FROM node:22-alpine AS build

WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
RUN npm run build

FROM nginx:stable-alpine AS runtime

ENV BACKEND_HOST=backend BACKEND_PORT=8080
COPY --from=build /build/dist/ /usr/share/nginx/html/
COPY deploy/nginx.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=12 \
  CMD wget -q -O /dev/null http://127.0.0.1/
