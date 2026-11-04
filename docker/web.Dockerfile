# syntax=docker/dockerfile:1.7
# 운영용 웹 진입점: frontend 정적 빌드 + 원격 실행기 ZIP + Caddy(HTTPS, /api 프록시)
FROM node:24.21.0-slim AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# scripts/package-crawl-agent.ps1 과 같은 파일 구성
FROM alpine:3.22 AS agent
RUN apk add --no-cache zip
WORKDIR /pkg
COPY crawler-service/package.json crawler-service/package-lock.json ./
COPY crawler-service/remote-agent/agent.js crawler-service/remote-agent/engine.js \
     crawler-service/remote-agent/walker.js crawler-service/remote-agent/screen.js \
     crawler-service/remote-agent/navigation.js crawler-service/remote-agent/language.js \
     crawler-service/remote-agent/retry.js crawler-service/remote-agent/probe.js \
     crawler-service/remote-agent/protocol.js crawler-service/remote-agent/Install.ps1 \
     crawler-service/remote-agent/Start-Agent.ps1 remote-agent/
COPY docs/REMOTE_CRAWL_AGENT.md README.md
RUN zip -r -X /autotest-crawl-agent.zip .

FROM caddy:2.10.2-alpine
COPY docker/Caddyfile /etc/caddy/Caddyfile
COPY --from=frontend /src/frontend/dist /srv
COPY --from=agent /autotest-crawl-agent.zip /srv/downloads/autotest-crawl-agent.zip
