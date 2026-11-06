# syntax=docker/dockerfile:1.7
#   docker build -t examgrid-web frontend

FROM node:25-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# The official image renders /etc/nginx/templates/*.template with envsubst at start-up.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/spa-headers.conf /etc/nginx/snippets/spa-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
ENV API_UPSTREAM=api:8080 \
    PORT=80
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s CMD wget -qO- http://127.0.0.1/healthz >/dev/null || exit 1
