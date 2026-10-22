# =====================================================================================
# Frontend image — build the SPA, then serve static files from nginx.
#
#   docker build -f infrastructure/docker/frontend.Dockerfile -t realtime-chat-frontend frontend
#
# The build context is `frontend/`, so nothing outside it (backend sources, .env files) is sent to the
# daemon. The SPA is built for a same-origin deployment: /api and /ws are proxied by the edge nginx.
# =====================================================================================
FROM node:22-alpine AS build
WORKDIR /app

# Dependency layer first: package.json and the lockfile change far less often than src/.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .

# Empty by default: relative URLs, one origin, no CORS. Override only for a split-origin deployment.
ARG VITE_API_BASE=""
ENV VITE_API_BASE=${VITE_API_BASE}
RUN npm run build

# -------------------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx-spa.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
  CMD wget -q -O /dev/null http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
