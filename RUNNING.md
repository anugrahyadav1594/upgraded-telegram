# Running REALCHAT

Every command needed to run, test and operate this application. Commands are written for bash on
Linux/macOS; on Windows use **Git Bash** or **WSL** (the `./mvnw` wrapper and `docker compose` work
identically there).

Two ways to run it:

| | Use it for | Time to first request |
| --- | --- | --- |
| **[Path A — Docker Compose](#path-a-everything-in-docker)** | Demos, grading, anything you want to "just work" | ~2 minutes (mostly the first Maven build) |
| **[Path B — Local development](#path-b-local-development-hot-reload)** | Editing code, hot reload on both sides | ~1 minute after dependencies are cached |

---

## 0. Prerequisites

| Path | Needs |
| --- | --- |
| A (Compose) | Docker Engine/Desktop 24+ with Compose v2, 4 GB RAM free. **Nothing else** — no JDK, no Node, no PostgreSQL |
| B (local) | JDK 21, Node 20.19+, Docker (for PostgreSQL + Redis, or install them natively), plus `./mvnw` which is bundled |

Check what you have:

```bash
docker --version && docker compose version    # Path A + B
node --version && npm --version                # Path B (Node 20.19+)

# Path B also needs a JDK — and the check is JAVA_HOME, not `java`, for reasons below:
echo "JAVA_HOME=$JAVA_HOME"
"$JAVA_HOME/bin/java" -version 2>/dev/null || echo "JAVA_HOME is not pointing at a usable JDK"
```

### If `./mvnw` says "The JAVA_HOME environment variable is not defined correctly"

That message is Maven's, not the wrapper's, and it means Maven could not execute the JVM it resolved:
`$JAVA_HOME/bin/java` does not exist, and neither does a `java` on the PATH it inherited. Maven needs a
**JDK** (for `javac`), not a JRE — and setting `JAVA_HOME` is required even when plain `java -version`
already works in your shell.

Install a JDK 21 if you do not have one, then point `JAVA_HOME` at its directory (the one containing
`bin/java` and `bin/javac`):

**Windows — Git Bash or WSL**

```bash
# Git Bash: install with winget from PowerShell, or use the Adoptium installer, then:
ls "/c/Program Files/Eclipse Adoptium/"            # e.g. jdk-21.0.5.11-hotspot
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.5.11-hotspot"

# WSL / Ubuntu:
sudo apt update && sudo apt install -y openjdk-21-jdk
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
```

**macOS**

```bash
brew install openjdk@21
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
```

**Linux**

```bash
sudo apt install -y openjdk-21-jdk          # or your distribution's package
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
```

Confirm before running anything else:

```bash
echo "$JAVA_HOME"
"$JAVA_HOME/bin/java" -version      # → openjdk version "21.x"
"$JAVA_HOME/bin/javac" -version     # → javac 21.x  (if this is missing, you installed a JRE)
```

Make it permanent by adding the `export` line to `~/.bashrc` (Git Bash, WSL, Linux) or `~/.zshrc`
(macOS), then reopen the shell. On Windows, setting `JAVA_HOME` as a system environment variable works
too — Git Bash converts it automatically.

> None of this is needed for **Path A**: the Docker build compiles the backend inside a Maven image, so
> the host needs no JDK at all.

---

## Path A: everything in Docker

### A1. Get the code and configure it

```bash
git clone https://github.com/anugrahyadav1594/upgraded-telegram.git
cd upgraded-telegram

cp .env.example .env
```

Now edit `.env` and replace the `CHANGE_ME` values. The stack **refuses to start** while the
placeholders are present — that is deliberate, not a bug:

```bash
# Generate a session secret (paste the output into SESSION_SECRET in .env)
openssl rand -base64 48

# Generate database and redis passwords however you like, then set:
#   POSTGRES_PASSWORD=<something>
#   DATABASE_PASSWORD=<the same value>
#   REDIS_PASSWORD=<something>
#   SESSION_SECRET=<the openssl output>
```

> `--env-file .env` is passed explicitly in every command below. Compose resolves `.env` relative to
> the compose file's directory when `-f` points elsewhere, so being explicit is what makes these
> commands work from the repository root regardless of your Compose version.

Optional, to save typing:

```bash
alias rc='docker compose -f infrastructure/docker-compose.yml --env-file .env'
```

### A2. Start everything

```bash
docker compose -f infrastructure/docker-compose.yml --env-file .env up -d --build
```

This builds the backend (Maven, skips tests) and the frontend (Vite), then starts **postgres**,
**redis**, **backend**, **frontend** and **nginx**. The first build takes a few minutes because Maven
downloads the dependency tree; later builds reuse the cached layer unless `pom.xml` changed.

### A3. Confirm it is up

```bash
docker compose -f infrastructure/docker-compose.yml --env-file .env ps
curl -fsS http://localhost:8080/actuator/health/readiness
```

Expected: every service `running (healthy)`, and a JSON health payload with `"status":"UP"`.
Then open **<http://localhost:8080>**.

| URL | What |
| --- | --- |
| <http://localhost:8080> | The application (nginx serves the SPA and proxies `/api`, `/ws`) |
| <http://localhost:8080/swagger-ui.html> | Interactive API documentation |
| <http://localhost:8080/v3/api-docs> | OpenAPI JSON |
| <http://localhost:8080/actuator/health> | Health (add `/readiness` or `/liveness`) |

### A4. Sign in

**There are no built-in credentials.** A fresh database has no accounts, so either:

* **Register your own** at <http://localhost:8080/register> — the username is your public handle, the
  email address is used only for sign-in and password recovery and is never shown to anyone; or
* **Seed the four development accounts** with the next step, whose credentials are:

| Username | Email (also accepted at sign-in) | Password |
| --- | --- | --- |
| `@alex` | `alex@example.test` | whatever you set as `DEV_SEED_PASSWORD` |
| `@ghost` | `ghost@example.test` | same |
| `@developer` | `developer@example.test` | same |
| `@testuser` | `testuser@example.test` | same |

Sign-in accepts **either** the username or the email address.

### A5. Optional: the development accounts

The seed creates `@alex`, `@ghost`, `@developer` and `@testuser` with obviously-marked `[DEV ACCOUNT]`
profiles. It contains **no plaintext password**: it takes an Argon2id hash, and refuses to run without
one.

**What `SEED_PASSWORD_HASH` is:** the Argon2id hash of `DEV_SEED_PASSWORD`, in the PHC format the
database stores:

```
$argon2id$v=19$m=19456,t=2,p=1$<16-byte salt, base64>$<32-byte hash, base64>
   │       │      │        │        │        └── the hash itself
   │       │      │        │        └────────── the random salt, unique per password
   │       │      │        └─────────────────── p=1 lane, t=2 passes, m=19 MiB (OWASP baseline)
   │       │      └──────────────────────────── Argon2 version 19
   │       └─────────────────────────────────── Argon2id — memory-hard, GPU-resistant
   └─────────────────────────────────────────── the standard password-hash prefix
```

Two different values are involved and they are easy to mix up:

| Value | Where it goes | What it is |
| --- | --- | --- |
| `DEV_SEED_PASSWORD` | `.env` | The password you **type** when signing in as `@alex` etc. The application never reads this variable — it exists only as the input to the command below. |
| `SEED_PASSWORD_HASH` | `.env`, then the database | The **hashed** form, which is the only thing the seed (and the database) ever sees |

The seed **refuses to run without it**, and rejects anything that is not an `$argon2id$…` string. That
is the point: a seed file with a plaintext password, or with a hard-coded hash, is a credential leak
waiting to be reused on a real deployment.

**Generate your own (recommended):**

```bash
# 1. Generate the hash from the built backend image, using the same encoder that verifies logins.
docker compose -f infrastructure/docker-compose.yml --env-file .env \
  run --rm --entrypoint sh backend -c 'java -jar /app/app.jar --hash-password "$DEV_SEED_PASSWORD"'

# 2. Paste the single $argon2id$… line it prints into .env as SEED_PASSWORD_HASH.

# 3. Apply the seed.
docker compose -f infrastructure/docker-compose.yml --env-file .env \
  --profile seed run --rm seed
```

**Or use this ready-made pair** (a development convenience — it is a *public* password, so use it only
on a database nobody else can reach):

```bash
# In .env:
DEV_SEED_PASSWORD=dev-only-Passphrase-2026!
SEED_PASSWORD_HASH=$argon2id$v=19$m=19456,t=2,p=1$a7gwIaUfuXLImcxjrEFHWQ$zpsiws/kERMz5fKzep5H2ueSvq0g66nDRvte8kH7BoM
```

> Quote the hash in `.env` if your shell or `.env` parser struggles with the `$` characters:
> `SEED_PASSWORD_HASH='$argon2id$…'`. Compose passes it through verbatim either way; the `$` only
> matters where a shell expands the value.

Sign in as `@alex`, `@ghost`, `@developer` or `@testuser` with `DEV_SEED_PASSWORD`. Note the asymmetry:
**the hash goes in `.env`; the plaintext is what you type at the login screen.**

Needing something other than Docker? The hash is standard PHC Argon2id, so any implementation works —
for example `python -m pip install argon2-cffi` and hash with `time_cost=2, memory_cost=19456,
parallelism=1, hash_len=32, salt_len=16`, type `ID`. The parameters are the ones in
[docs/security-design.md](docs/security-design.md) §3 and must match, or sign-in will reject the hash.

### A6. Logs, restart, stop

```bash
# Follow logs
docker compose -f infrastructure/docker-compose.yml --env-file .env logs -f backend

# Restart one service after changing .env
docker compose -f infrastructure/docker-compose.yml --env-file .env up -d backend

# Rebuild after changing code
docker compose -f infrastructure/docker-compose.yml --env-file .env up -d --build

# Stop, keep the data volumes
docker compose -f infrastructure/docker-compose.yml --env-file .env down

# Stop and delete all data (fresh database on next start)
docker compose -f infrastructure/docker-compose.yml --env-file .env down -v
```

---

## Path B: local development (hot reload)

Both sides reload on save: Spring Boot DevTools restarts the backend, Vite hot-reloads the frontend.

### B1. Start PostgreSQL and Redis

Either reuse the compose services (loopback-only ports, so nothing on your LAN can reach them):

```bash
docker compose -f infrastructure/docker-compose.yml --env-file .env up -d postgres redis
```

…or start throwaway containers that match the `dev` profile defaults exactly, so no environment
variables are needed at all:

```bash
docker run --rm -d --name realchat-postgres \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=realtime_chat \
  -p 127.0.0.1:5432:5432 postgres:16-alpine

docker run --rm -d --name realchat-redis -p 127.0.0.1:6379:6379 redis:7-alpine
```

### B2. Backend

If you used the throwaway containers above, the `dev` profile defaults already match — just run it:

```bash
cd backend
./mvnw spring-boot:run                    # dev profile is the default → http://localhost:8080
```

If you used the compose services, point the backend at them. Compose credentials live in `.env`, but
**Spring Boot does not read `.env`** — export the values in your shell, using `localhost` (not the
compose service name, which only resolves inside the container network):

```bash
cd backend
export SPRING_PROFILES_ACTIVE=dev
export DATABASE_URL=jdbc:postgresql://localhost:5432/realtime_chat
export DATABASE_USERNAME="${POSTGRES_USER:-realtime_chat}"
export DATABASE_PASSWORD="$(grep -E '^POSTGRES_PASSWORD=' ../.env | cut -d= -f2-)"
export REDIS_HOST=localhost
export REDIS_PASSWORD="$(grep -E '^REDIS_PASSWORD=' ../.env | cut -d= -f2-)"

./mvnw spring-boot:run
```

Flyway applies `V1`–`V7` on start-up; if the schema and the entities disagree, startup **fails** with
Hibernate's `ddl-auto=validate` error — that is the intended behaviour, not a misconfiguration.

Verify:

```bash
curl -fsS http://localhost:8080/actuator/health
open http://localhost:8080/swagger-ui.html      # macOS; use xdg-open on Linux
```

### B3. Frontend

```bash
cd frontend
npm install
npm run dev                                # http://localhost:5173
```

Open <http://localhost:5173>. The dev server proxies `/api`, `/ws` and `/actuator` to
`http://localhost:8080`, so the browser sees a single origin — the same shape as production, which is
why cookies and CSRF behave identically in both.

If the backend is on another host or port:

```bash
cp .env.example .env.local
# set VITE_BACKEND_URL=http://192.168.1.50:8080, then restart npm run dev
```

---

## Path C: the interface only (no Java, no Docker)

When the goal is to *look* at the UI — a design review, a screenshot, showing someone the product —
none of the backend is needed. `tools/preview/server.js` answers the documented API contract from memory
and speaks the same STOMP frames over `/ws`.

```bash
node tools/preview/server.js                 # terminal 1 — stub API + realtime on :8080
cd frontend && npm install && npm run dev    # terminal 2 — the app on :5173
open http://localhost:5173
```

Sign in with any username and password; there is nothing to validate against. The fixture data covers
unread and muted conversations, edited and deleted messages, reactions, read receipts and
notifications, so most screens have something to show.

**If the interface feels slow, this is the fix.** `npm run dev` sends the app as ~90 separate,
uncompressed module requests; the built bundle is ~10 compressed ones, which on a modest connection is
the difference between a few seconds of blank screen and a fraction of a second. Measured here on the
same machine and network conditions (1.6 Mbps, 150 ms RTT):

| | Requests | Transferred | Time to a usable screen |
| --- | --- | --- | --- |
| `npm run dev` | 88 | 6.6 MB | 2.6 s |
| `npm run build && npm run preview` | 8 | 0.44 MB | 0.6 s |

```bash
cd frontend
npm run build                                # then
npm run preview                              # http://localhost:4173, same /api and /ws proxy
```

Use `dev` while editing (hot reload) and `preview` whenever you are just looking at, demoing or
screenshotting the app.

**This is not the backend.** It is development tooling, it is not wired into the application, the build
or the tests, and it deliberately implements only what the UI reads. Anything to do with persistence,
authentication, rate limits or moderation has to be exercised against Path A or Path B.

Two handy controls while the stub is running:

```bash
# Push a realtime frame at every open browser (typing, a new message, a read receipt, …)
curl -X POST http://localhost:8080/__preview/emit \
  -H 'Content-Type: application/json' \
  -d '{"type":"typing.started","payload":{"conversationId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
       "userId":"22222222-2222-4222-8222-222222222222","displayName":"Ghost","expiresInMs":6000}}'

# Put the fixtures back as they were
curl -X POST http://localhost:8080/__preview/reset -H 'Content-Type: application/json' -d '{}'
```

### Regenerating the screenshots

```bash
node tools/preview/server.js                 # terminal 1
cd frontend && npm run dev                   # terminal 2
cd tools/preview && npm install              # puppeteer-core only; it drives a browser you already have
node capture.mjs                              # writes docs/screenshots/*.png
```

`capture.mjs` finds Chrome or Edge automatically; set `BROWSER_PATH=/path/to/chrome` if yours is
somewhere unusual. On a machine with no browser at all, `npm install --no-save @sparticuz/chromium`
provides one and the script will use it.

---

## Tests and checks

### Frontend

```bash
cd frontend
npm run lint          # eslint (flat config, react + hooks rules)
npm test              # Vitest unit tests
npm run test:watch    # re-run on change
npm run build         # production bundle → dist/
```

Current state: lint clean, **25/25 tests passing**, build succeeds.

### Backend

```bash
cd backend
./mvnw -B test                 # unit tests only
./mvnw -B verify               # unit + integration; integration tests use Testcontainers → needs Docker
./mvnw -B -DskipTests package  # build the jar without tests
java -jar target/*.jar         # run the packaged jar (profile from SPRING_PROFILES_ACTIVE)
```

> The backend has never been compiled in the environment where it was authored (no JDK, no route to
> Maven Central there). Run `./mvnw verify` here — or let CI do it — to close that gap. See the status
> note in [README.md](README.md).

### Database and Java source verification (offline tooling)

```bash
python3 -m venv /tmp/verify-venv
/tmp/verify-venv/bin/pip install pglast psycopg2-binary pgserver tree-sitter tree-sitter-java

# Applies V1–V7 and the seed to a throwaway PostgreSQL, then runs 53 assertions
/tmp/verify-venv/bin/python tools/verify/sql_verify.py --execute

# …against an existing server instead
/tmp/verify-venv/bin/python tools/verify/sql_verify.py --execute \
  --dsn "postgresql://realtime_chat:PASSWORD@localhost:5432/realtime_chat"

# Structural checks on the Java sources: parsing, package↔directory, imports,
# entity columns vs. the migrations
/tmp/verify-venv/bin/python tools/verify/java_static_check.py --strict
```

`sql_verify.py` never touches your development database unless you pass `--dsn`.

### End-to-end (Playwright)

```bash
# The stack must be running first (Path A), because the suite drives the real application
docker compose -f infrastructure/docker-compose.yml --env-file .env up -d --build

cd tests/e2e
npm install
npx playwright install --with-deps chromium
npx playwright test

# Point it somewhere else
BASE_URL=http://localhost:8080 npx playwright test

# Watch it run, or inspect a failure
npx playwright test --headed
npx playwright show-report
```

The mandatory journey is `tests/e2e/full-conversation-journey.spec.js`: two users register in two
isolated browser contexts, find each other, exchange messages in real time, then exercise read
receipts, editing, deletion and reload persistence.

---

## Common operations

```bash
rc='docker compose -f infrastructure/docker-compose.yml --env-file .env'

# Database shell
$rc exec postgres psql -U realtime_chat -d realtime_chat

# Redis shell (presence keys, typing TTLs)
$rc exec redis redis-cli

# Reset the database (drops volumes, migrations re-run on next start)
$rc down -v && $rc up -d --build

# Tail one service
$rc logs -f --tail=200 backend

# Rebuild only the frontend after UI changes
$rc up -d --build frontend

# Which migrations has Flyway applied?
$rc exec postgres psql -U realtime_chat -d realtime_chat \
  -c 'select version, description, success from flyway_schema_history order by installed_rank;'
```

Changing the profile: `SPRING_PROFILES_ACTIVE=prod` requires real values — `SESSION_COOKIE_SECURE=true`,
`MAIL_ENABLED=true` with SMTP credentials, an `https://` `APP_PUBLIC_URL`, and a non-placeholder
`SESSION_SECRET`. The application **refuses to start** otherwise (`ProductionReadinessValidator`),
because a production server with development settings is worse than one that does not come up.

---

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `set POSTGRES_PASSWORD in .env` | `.env` missing, or Compose looked in `infrastructure/` | Pass `--env-file .env` (as above) and check the file exists at the repo root |
| `The JAVA_HOME environment variable is not defined correctly` | `JAVA_HOME` is unset or points at a JRE, while Maven needs a JDK | Install JDK 21 and see [Java setup](#if-mvnw-says-the-java_home-environment-variable-is-not-defined-correctly). Or skip it entirely and use Path A |
| Frontend shows "Can’t reach the server" | The API is not running, or is on a different port | Start the backend (`cd backend && ./mvnw spring-boot:run`) and press **Try again**; check `VITE_BACKEND_URL` in `frontend/.env.local` |
| "Checking your session…" hangs | The API is unreachable and nothing answers the request | Fixed by a request deadline: you now get an explanation within 8 seconds instead of an endless spinner. If it still hangs, the backend is accepting connections but not responding — check its logs |
| `bind: address already in use` | Something is already on 8080/5432/6379 | Change `HTTP_PORT` / `POSTGRES_HOST_PORT` / `REDIS_HOST_PORT` in `.env` |
| Frontend loads, every request 502s | Backend still starting (Flyway + first boot) | Wait for `curl http://localhost:8080/actuator/health/readiness`, then `$rc logs backend` |
| Redirected to the login screen immediately after signing in | Cookie dropped: cross-origin frontend | Serve the SPA from the same origin (nginx, as in Path A), or set `CORS_ALLOWED_ORIGINS` and `SESSION_SAME_SITE` deliberately |
| WebSocket stuck on "Reconnecting…" | `Origin` not allowed, or a proxy missing upgrade headers | Add the origin to `CORS_ALLOWED_ORIGINS`; nginx needs `proxy_set_header Upgrade`/`Connection` (already set in `infrastructure/nginx/nginx.conf`) |
| Backend container restarts in a loop | Invalid production configuration | `$rc logs backend` — the validator lists every problem it found |
| `npm install` fails with ERESOLVE | eslint 10 vs `eslint-plugin-react` peer range | Use the pinned `eslint@^9.39.5` from `package-lock.json` (`npm ci`) |
| Testcontainers tests fail | Docker not running/reachable | Start Docker, or run `./mvnw test` (unit tests only) |
| `docker compose` cannot find the compose file | Wrong working directory | Run from the repository root, or use `-f` with an absolute path |

---

## Where to read next

| Document | Contents |
| --- | --- |
| [README.md](README.md) | What the project is and how it is put together |
| [docs/deployment.md](docs/deployment.md) | Configuration reference, hardening checklist, backups, zero-downtime deploys |
| [docs/architecture.md](docs/architecture.md) | Module boundaries and request flow |
| [docs/api-specification.md](docs/api-specification.md) | Every endpoint, payload and error |
| [docs/websocket-protocol.md](docs/websocket-protocol.md) | Realtime destinations, frames and resync semantics |
| [docs/testing-strategy.md](docs/testing-strategy.md) | What is tested at which level, and the mandatory E2E scenario |
