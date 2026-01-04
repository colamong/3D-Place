# ---- build stage (Vite) ----
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json* ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY . .

ARG VITE_ION_TOKEN
ENV VITE_ION_TOKEN=$VITE_ION_TOKEN

RUN npx vite build

# ---- runtime stage (rootless nginx on 8080) ----
FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY .docker/nginx.conf /etc/nginx/conf.d/default.conf

USER root
RUN mkdir -p /run \
    && chown -R nginx:nginx \
       /usr/share/nginx/html \
       /var/cache/nginx \
       /var/run \
       /run \
       /etc/nginx

EXPOSE 8080

USER nginx
