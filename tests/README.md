# Tests

| Directory | Level | Runner | What it proves |
| --- | --- | --- | --- |
| `e2e/` | End-to-end | Playwright | The whole product works through a real browser against the full stack |
| `integration/` | Cross-module integration | JUnit 5 + Testcontainers | Modules work together against real PostgreSQL and Redis |
| `performance/` | Load & soak | k6 | The stated latency and throughput budgets hold |

Most automated tests live next to the code they test (`backend/src/test/java`, `frontend/src/**/*.test.jsx`)
so they run in the same build as the code; this directory holds the cross-cutting suites that need a
running stack. See [../docs/testing-strategy.md](../docs/testing-strategy.md) for the full pyramid and the
rationale for each level.

## End-to-end

```bash
docker compose -f infrastructure/docker-compose.yml up -d --build   # full stack
cd tests/e2e && npm install && npx playwright install --with-deps
npx playwright test                                                 # BASE_URL defaults to http://localhost:8080
```

The mandatory scenario is `e2e/full-conversation-journey.spec.js`: two users register in two isolated
browser contexts, find each other by username, exchange messages in real time, and exercise read
receipts, editing, deletion, blocking and a reload-persistence check.

Playwright is deliberately the only heavy dependency here: the suite drives the real application through
the same entry point a person uses, which is the only way to catch the class of bug that lives between
"the API returns 201" and "the message appears".
