FROM nginx:stable-alpine

ENV BACKEND_HOST=backend BACKEND_PORT=8080
COPY frontend/dist/ /usr/share/nginx/html/
COPY deploy/nginx.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=12 \
  CMD wget -q -O /dev/null http://127.0.0.1/
