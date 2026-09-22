# Deployment

Three supported ways to run the system, from simplest to most production-like.

---

## 1. Prerequisites

| Mode | Requires |
| --- | --- |
| Docker Compose (recommended) | Docker 24+, Docker Compose v2, 4 GB RAM free |
| Local development | JDK 21, Maven 3.9+ (or the bundled `./mvnw`), Node 20.19+, PostgreSQL 16+, Redis 7+ |
| Production | Docker host or Kubernetes, TLS certificate, PostgreSQL 16+ (managed ok), Redis 7+ (managed ok), SMTP credentials |

---

## 2. Docker Compose (everything, one command)

```bash
git clone <repository-url> realtime-chat
cd realtime-chat
cp .env.example .env          # then edit: at minimum POSTGRES_PASSWORD and SESSION_SECRET
docker compose -f infrastructure/docker-compose.yml --env-file .env up -d --build
```

What starts:

| Service | Image / build | Port | Health check |
| --- | --- | --- | --- |
| `postgres` | `postgres:16-alpine` | 5432 (internal) | `pg_isready -U $POSTGRES_USER` |
| `redis` | `redis:7-alpine` | 6379 (internal) | `redis-cli ping` |
| `backend` | `infrastructure/docker/backend.Dockerfile` (multi-stage: Maven build → JRE 21 runtime) | 8080 | `wget -qO- http://localhost:8080/actuator/health/readiness` |
| `frontend` | `infrastructure/docker/frontend.Dockerfile` (Node build → nginx static) | 80 | `wget -qO- http://localhost/` |
| `nginx` | `infrastructure/nginx/nginx.conf` | 8080 (host) | route check |

Verify:

```bash
curl -fsS http://localhost:8080/actuator/health/readiness | jq
open http://localhost:8080              # the SPA (nginx proxies /api and /ws to backend)
```

Seed development data (development only, never production):

```bash
docker compose -f infrastructure/docker-compose.yml exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -v seed_password="$(openssl rand -base64 18)" \
  -f /docker-entrypoint-initdb.d/seed/dev_seed.sql
```

(Seeding is also wired as an optional compose profile: `--profile seed`.)

Logs / teardown:

```bash
docker compose -f infrastructure/docker-compose.yml logs -f backend
docker compose -f infrastructure/docker-compose.yml down          # keep volumes
docker compose -f infrastructure/docker-compose.yml down -v       # also delete data
```

---

## 3. Local development (hot reload)

### Infrastructure only

```bash
docker compose -f infrastructure/docker-compose.yml --env-file .env up -d postgres redis
```

Alternatively, start containers that match the `dev` profile defaults, so no environment variables are
needed at all:

```bash
docker run --rm -d --name realchat-postgres \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=realtime_chat \
  -p 127.0.0.1:5432:5432 postgres:16-alpine
docker run --rm -d --name realchat-redis -p 127.0.0.1:6379:6379 redis:7-alpine
```

### Backend

```bash
cd backend
./mvnw spring-boot:run                        # http://localhost:8080 (dev profile is the default)
```

Spring Boot does **not** read `.env` — that file is consumed by Docker Compose. When the backend runs
on your host against the compose services, export the values yourself (host names are `localhost`, not
the compose service names):

```bash
export SPRING_PROFILES_ACTIVE=dev
export DATABASE_URL=jdbc:postgresql://localhost:5432/realtime_chat
export DATABASE_USERNAME="${POSTGRES_USER:-realtime_chat}"
export DATABASE_PASSWORD="$(grep -E '^POSTGRES_PASSWORD=' ../.env | cut -d= -f2-)"
export REDIS_HOST=localhost
export REDIS_PASSWORD="$(grep -E '^REDIS_PASSWORD=' ../.env | cut -d= -f2-)"
```

- Flyway runs migrations on startup (`spring.flyway.enabled=true`).
- `spring.jpa.hibernate.ddl-auto=validate` — the schema must match the entities, or startup fails
  (that is the point).
- OpenAPI/Swagger UI: <http://localhost:8080/swagger-ui/index.html> (dev profile only).
- Health: <http://localhost:8080/actuator/health>.

### Frontend

```bash
cd frontend
npm install
npm run dev                                   # http://localhost:5173, proxies /api and /ws → :8080
npm test                                      # Vitest
npm run build                                 # dist/ (static assets for nginx)
```

The Vite dev server proxies API and WebSocket traffic to the backend, so the SPA runs same-origin
(cookies and CSRF work exactly as in production).

---

## 4. Environment variables

Copy `.env.example` → `.env`. Nothing secret is ever committed.

| Variable | Required | Default | Notes |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | no | `dev` | `dev`, `docker`, `prod` |
| `SERVER_PORT` | no | `8080` | |
| `DATABASE_URL` | yes | — | `jdbc:postgresql://postgres:5432/realtime_chat` (compose service name!) |
| `DATABASE_USERNAME` | yes | — | |
| `DATABASE_PASSWORD` | yes | — | production profile fails fast when empty |
| `DATABASE_POOL_SIZE` | no | `10` | Hikari maximum pool size |
| `REDIS_HOST` | yes | `redis` | |
| `REDIS_PORT` | no | `6379` | |
| `REDIS_PASSWORD` | prod | — | |
| `SESSION_SECRET` | prod | — | HMAC key for CSRF/cursor signing; ≥ 32 random bytes; app refuses to start in `prod` with the placeholder |
| `POSTGRES_HOST_PORT` / `REDIS_HOST_PORT` | no | `5432` / `6379` | Host ports published by compose **on 127.0.0.1 only**, for local tooling (`psql`, `redis-cli`, a backend running on the host) |
| `SESSION_COOKIE_SECURE` | no | `true` (`false` in dev over http) | |
| `SESSION_IDLE_TIMEOUT` | no | `7d` | idle expiry |
| `SESSION_ABSOLUTE_TIMEOUT` | no | `30d` | absolute expiry |
| `MAIL_ENABLED` | no | `false` | dev logs the reset link; production must set `true` |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | when `MAIL_ENABLED=true` | — | SMTP |
| `MAIL_FROM` | no | `no-reply@realtime-chat.local` | |
| `APP_PUBLIC_URL` | yes | `http://localhost:5173` | used in password-reset links |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:5173,http://localhost:8080` | comma-separated; **never** `*` with credentials |
| `RATE_LIMIT_ENABLED` | no | `true` | disable only in tests |
| `LOG_LEVEL_ROOT` / `LOG_LEVEL_APP` | no | `INFO` / `DEBUG` (dev) | |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | no | — | optional metrics/traces export |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | seed only | — | creates the moderator account in `dev` seed; never used in production |

Frontend build-time variables (`frontend/.env`): `VITE_API_BASE_URL` (default `/api/v1`),
`VITE_WS_URL` (default same-origin `/ws`) — the defaults are the production values; only override for
a split-origin deployment.

---

## 5. Database migrations

- Tool: **Flyway**, files in `backend/src/main/resources/db/migration` (`V1__…sql`, mirrored into
  `database/migrations` for infra tooling).
- Applied automatically on backend startup; also runnable standalone:

```bash
cd backend && ./mvnw flyway:info flyway:migrate -Dflyway.url="$DATABASE_URL" \
  -Dflyway.user="$DATABASE_USERNAME" -Dflyway.password="$DATABASE_PASSWORD"
```

- Rules: migrations are **append-only** (never edit an applied file — Flyway checksums will fail);
  each migration is transactional (`BEGIN … COMMIT` via Flyway's default), forward-only, and contains
  its own indexes/constraints. Rollbacks are new forward migrations (documented per migration when
  destructive).
- Zero-downtime pattern for column drops: add nullable column → dual-write via code → backfill
  migration → switch reads → drop in a later release.
- Baseline for an existing database: `flyway baseline` (documented, not automated).

---

## 6. Production considerations

### Hardening checklist

- [ ] `SPRING_PROFILES_ACTIVE=prod`, `SESSION_SECRET` set to a random ≥ 32-byte value.
- [ ] TLS terminated at the edge (nginx/Kubernetes ingress/ALB); HTTP → HTTPS redirect; HSTS on.
- [ ] `SESSION_COOKIE_SECURE=true`; `CORS_ALLOWED_ORIGINS` set to the real origin(s) only.
- [ ] Database: dedicated non-superuser role, TLS to PostgreSQL (`sslmode=require`), connection pool
      sized to the DB's `max_connections` minus headroom, backups enabled (**nightly `pg_dump` +
      WAL archiving**; restore drill documented below).
- [ ] Redis: `--requirepass`, persistence (`appendonly yes`) for rate-limit/presence survival,
      `maxmemory-policy noeviction` (never evict session-cache keys silently — expiry handles it).
- [ ] SMTP credentials configured; `MAIL_ENABLED=true`; SPF/DKIM aligned for the sending domain.
- [ ] `/actuator/**` restricted to the monitoring network (only `health` public), `prometheus`
      scraped by the monitoring stack with `ADMIN` credentials.
- [ ] Log aggregation (JSON logs → Loki/ELK) with `X-Request-Id` correlation; alerts on 5xx rate,
      handshake rejection rate, message-commit failures, DB pool saturation, Redis latency.
- [ ] Resource limits: backend `2 vCPU / 2 GB` per instance minimum, `-XX:MaxRAMPercentage=70`.
- [ ] Scaling: REST/WS instances are stateless (scale horizontally); the only single-instance
      component is the in-process STOMP simple broker — move to a broker relay before scaling
      sockets beyond one instance per conversation set.

### Backup & restore drill

```bash
# backup
pg_dump --format=custom --no-owner "$DATABASE_URL" > backup-$(date +%F).dump
# restore into a scratch database and verify row counts
createdb scratch && pg_restore --no-owner --dbname=scratch backup-2026-09-22.dump
psql scratch -c "select count(*) from messages;"
```

Targets: **RPO ≤ 5 min** (WAL archiving), **RTO ≤ 1 h** (documented restore procedure).

### Zero-downtime deploy

1. Deploy the new backend version **without** running destructive migrations (additive only).
2. Flyway applies additive migrations on startup (fast, online; `CREATE INDEX CONCURRENTLY` for large
   tables is run out-of-band as a documented manual step, since Flyway runs in a transaction).
3. Roll instances one at a time; WebSocket clients reconnect automatically (see
   [websocket-protocol.md](websocket-protocol.md#5-reconnection--resynchronisation)) and resync.
4. Remove the previous version's dead code in a later release; only then drop columns.

### Observability

| Signal | Where |
| --- | --- |
| Health | `GET /actuator/health`, `/liveness`, `/readiness` |
| Metrics | `GET /actuator/prometheus` (Micrometer): HTTP, WS sessions, message throughput, fan-out latency, Redis/JDBC pools |
| Logs | JSON (`logback-spring.xml`), `requestId`, `userId`, event names; never credentials/content |
| Traces (optional) | OTLP exporter via `OTEL_EXPORTER_OTLP_ENDPOINT`; `chat.send` spans cover persist + fan-out |

### Known operational limits

| Limit | Value | Mitigation path |
| --- | --- | --- |
| Single-instance STOMP simple broker | 1 instance | broker relay (RabbitMQ/ActiveMQ) |
| PostgreSQL write throughput | ~5–10 k msgs/s on a modest VM | partition `messages` by month + archival |
| Redis Pub/Sub (at-most-once fan-out) | lossy on instance crash | clients resync from REST (already implemented) |
| Attachment storage | not enabled | object storage + signed URLs (roadmap) |

---

## 7. Continuous integration

The pipeline is defined in `infrastructure/ci/ci.yml`. Enable it once per repository:

```bash
mkdir -p .github/workflows && cp infrastructure/ci/ci.yml .github/workflows/ci.yml
git add .github/workflows/ci.yml && git commit -m "Enable CI"
```

(It ships outside `.github/` because the GitHub App used to prepare this repository cannot create
files under `.github/workflows` — its token lacks the `workflows` permission, and GitHub rejects the
push. The content is identical either way.)

Once enabled, it runs on every push/PR:

```
backend   ./mvnw -B verify                     # unit + slice + integration (Testcontainers)
frontend  npm ci && npm run lint && npm run test -- --coverage && npm run build
security  dependency review · gitleaks · tools/verify/java_static_check.py · sql_verify.py
e2e       docker compose up -d --build && npx playwright test
```

Artifacts: Surefire/Failsafe reports, JaCoCo/Vitest coverage, Playwright traces and screenshots on
failure. Migrations are protected by a job that rejects modifications to previously merged `V*__`
files.

---

## 8. Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| Backend exits on startup with `FlywayValidateException` | Edited an applied migration | restore the file; add a new migration |
| `401` on every request behind a proxy | `SESSION_COOKIE_SECURE=true` over plain HTTP, or missing `X-Forwarded-Proto` | set `server.forward-headers-strategy=framework` (default in compose) / use HTTPS |
| `set POSTGRES_PASSWORD in .env` at start-up | `.env` missing at the root, or Compose resolved it relative to the compose file's directory | pass `--env-file .env` explicitly (as in §2) |
| Cookie not set at all | Cross-site frontend origin | serve the SPA through the same origin (nginx) or set `CORS_ALLOWED_ORIGINS` + `SameSite=None; Secure` |
| WebSocket stuck `RECONNECTING` | `Origin` not allow-listed, or proxy missing upgrade headers | add the origin / check `proxy_set_header Upgrade` in nginx |
| Messages appear with a delay | slow consumer disconnected, or the peer is offline (by design) | expected: offline users get history on next load |
| `429` during development | rate limits active | set `RATE_LIMIT_ENABLED=false` in the dev `.env` |
| Testcontainers fails | no Docker daemon | start Docker, or run `./mvnw test -DexcludedGroups=integration` for the fast suite |
