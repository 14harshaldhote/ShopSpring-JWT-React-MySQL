# syntax=docker/dockerfile:1
# Builds the React app, then serves it with nginx as a non-root user. nginx is also the
# reverse proxy for the API, so browser and API share one origin (no CORS needed).
ARG REGISTRY=docker.io/library

FROM ${REGISTRY}/node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM ${REGISTRY}/nginx:alpine
RUN rm /etc/nginx/conf.d/default.conf \
    && chown -R nginx:nginx /var/cache/nginx /var/log/nginx /etc/nginx/conf.d
COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY nginx/shopspring.conf /etc/nginx/conf.d/shopspring.conf
COPY --from=build --chown=nginx:nginx /src/dist /usr/share/nginx/html
USER nginx
EXPOSE 8080
