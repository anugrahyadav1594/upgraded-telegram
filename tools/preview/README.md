# tools/preview — interface preview harness

Development-only. This directory exists so the React frontend can be run, reviewed and screenshotted
without starting PostgreSQL, Redis and the Spring Boot backend. **Nothing here is part of the
application, the production build, or any test suite**; the only file the app itself ever touches is
`capture.mjs`'s browser, and the app never loads anything from this folder.

## What it is

`server.js` is an in-memory stand-in for the backend that answers the *documented* contract:

| Surface | What it does |
| --- | --- |
| `/api/v1/**` | The REST endpoints the UI reads and writes (auth, conversations, messages, notifications, privacy, blocks, sessions, presence, search), returning the same JSON shapes as the backend |
| `ws://…/ws` | STOMP over WebSocket: `CONNECT`, `SUBSCRIBE`, `SEND`, and the server→client frames (`message.ack`, `message.new`, `message.delivered`, `message.read.updated`, `typing.*`, `presence.updated`, …) with the payloads described in [`docs/websocket-protocol.md`](../../docs/websocket-protocol.md) |
| `/__preview/emit`, `/__preview/reset` | Test hooks used by `capture.mjs` to stage a scene. Namespaced so they can never collide with a real route |

Any credentials are accepted — sign in as `alex`, `alex@example.test`, or anything else. The fixture
data lives in `fixtures.js`: two people with conversations, one unread thread, one muted thread, and
messages covering edited, deleted, replied, reacted and read states.

## Running it

```bash
node tools/preview/server.js                 # :8080 (override with PREVIEW_PORT)
cd frontend && npm run dev                   # :5173 — the app proxies /api and /ws here
```

## Capturing the screenshots

```bash
node capture.mjs                             # after the two commands above; writes docs/screenshots/
```

`capture.mjs` drives a real browser over the real UI and fails if a screen does not render, so the
images cannot silently become pictures of an error page or a spinner. It uses a browser it finds on the
machine (Chrome/Edge; `BROWSER_PATH` to be explicit) via `puppeteer-core`, which is only a *dev*
dependency — `npm install` here is what pulls it in, and nothing else in the repository needs it.

## What it is not

It is not a mock for tests, not a fixture server for CI, and not a substitute for the backend. It has no
database, no real authentication, no persistence, no rate limiting and no moderation logic — anything
in those areas must be verified against the real stack (`RUNNING.md`, Path A or Path B).
