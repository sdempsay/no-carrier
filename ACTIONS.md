# Actions log

Work log for no-carrier. Task board: [GitHub Issues](https://github.com/sdempsay/no-carrier/issues). Thin index: `TODO.md`.

## 2026-09-29 — PBKDF2 credential hashing (Fixes #3)

- Core port `CredentialHashPort` (`hash` / `matches`).
- Store adapter `Pbkdf2CredentialHasher`: JDK PBKDF2-HMAC-SHA-256, injectable iterations (default 600000), URL-safe encoding `pbkdf2-sha256/<iterations>/<salt>/<dk>`.
- Constant-time compare via `MessageDigest.isEqual`. Wrong keys and malformed encodings fail closed. Plaintext never appears in the encoding.
- Seven hasher tests plus existing store tests; `mvn -DskipDocker package` green.

## 2026-09-29 — Aether memory and filesystem stores (Fixes #2)

- `@AetherStoreProviders` on `org.dempsay.agenthub.store.memory` and `.store.fs` (`scr = false`).
- Generated `Memory*` / `Fs*` adapters for Agent, credential, channel, and message DTOs.
- Round-trip `AgentDto` in memory, on a temp filesystem root, and after opening a new FS store on the same root.

## 2026-09-29 — First-slice Aether DTOs (Fixes #1)

- Body-only `@AetherRecord` records: `AgentDto`, `AgentCredentialDto`, `ChannelDto`, `MessageDto`.
- Store id / created / updated live on `AetherPersisted.metadata()`.
- Tombstone flags are `Boolean` because Aether builders null-check every component (Aether consumer note in `PRD-updated.md`).
- Eight builder tests.

## 2026-09-29 — GitHub issue board

- Origin `https://github.com/sdempsay/no-carrier`.
- Issues #1–#9 on milestone First slice; #10–#13 later.
- `TODO.md` is a thin index; `AGENTS.md` uses `gh` and `Fixes #N`.

## 2026-09-29 — Product name and README

- Public name **no-carrier**; Maven coordinates remain `org.dempsay.agenthub:agent-hub`.
- README recast as a BBS session (pre-logon banner, GENERAL messages, G-files, hang-up).
- Reactor version `1.1.0-SNAPSHOT`.

## 2026-09-29 — Scaffolding

- `PRD.md` and `ARCHITECTURE.md` from approved decisions through 80.
- Four-module Maven skeleton under `dempsay-felix-parent:1.1.0-SNAPSHOT`.
- Empty reactor packaged with `/opt/homebrew/bin/mvn -DskipDocker package`.

## 2026-09-29 — Agent registry and credential lifecycle (Fixes #4)

- Core ports `AgentRegistryPort`, `CredentialServicePort`, `BootstrapCredentialPort` in `core.port`.
- Implementation `AgentRegistryService` and `CredentialServiceImpl` in `core.service`.
- Agent CRUD with lifecycle states (`pending`, `active`, `suspended`, `revoked`) and status updates.
- Credential create/rotate/revoke with PBKDF2 hashing via `CredentialHashPort`.
- Uses Aether store interface pattern (`AetherPersisted` + `ExceptionalResponse`).
- In-memory tests pass; builds clean with zero checkstyle violations.

## 2026-09-29 — Public channels and messages (Fixes #5)

- Core ports `ChannelPort`, `MessagePort`, `NotificationPort` in `core.port`.
- Implementation `ChannelService`, `MessageService`, `NoOpNotificationPort` in `core.service`.
- Public channel CRUD with slug uniqueness, soft-delete tombstones.
- Message create/reply with threadId/parentMessageId for nested replies.
- Cursor-based pagination: `before`/`after` exclusive, default limit 50, max 100.
- Message delete creates tombstone (empty body, preserved thread references).
- In-memory tests pass; builds clean with zero checkstyle violations.

## 2026-09-29 — Bearer auth and authorization port (Fixes #6)

- Core ports `BearerAuthPort`, `AuthorizationPort` in `core.port`.
- `AuthPrincipal` record with agentId and isAdmin flag.
- `BearerAuthService`: validates API key via PBKDF2, returns principal or failure.
- `AuthorizationService`: hybrid model - admins full access, agents read/post public channels, self-status updates.
- Capabilities do not grant permissions.
- Unknown/revoked/expired keys fail closed.
- In-memory tests pass; builds clean with zero checkstyle violations.

## 2026-09-29 — Application bootstrap and HTTP server (Fixes #7)

- Config: data dir (CLI → `AGENT_HUB_DATA_DIR` → `~/.agent-hub/data`), bind address, port, message size, heartbeat timeout, PBKDF2 iterations.
- First start with empty store: generates one-time admin key, prints once.
- JDK `HttpServer` + Gson in `org.dempsay.agenthub.app.http`.
- Every response includes `X-Request-Id` (honors valid client ID, else mints UUID).
- Structured errors: `code`, `message`, `requestId`, optional `violations`.
- Health endpoint at `/health` returns `{ "status": "UP", "ready": true }`.
- Core never sees `HttpExchange`, Gson types, or HTTP status codes.
- Builds clean with zero checkstyle violations; in-memory tests pass.

## 2026-09-29 — End-to-end proof (Fixes #9)

- 64 tests green: `mvn -DskipDocker package` with zero Checkstyle violations.
- `CoreServicesInMemoryTest` (21) — agent/credential/channel/message/auth/authorization against memory stores.
- `FilesystemRestartTest` (2) — temp root, re-bind new store instances, history and tombstones survive.
- `HttpApiTest` (21) — real JDK `HttpClient` on an ephemeral port: bearer auth, request ids, structured errors, slug/UUID resolution, cursors, `413` before persistence.
- `SmokeTest` (2) — start, bootstrap admin, register agent, open channel, post + reply, heartbeat, restart, history intact.
- New `HubRuntime` assembles the hub and picks memory vs filesystem stores from `AppConfig.persistent`; `AgentHubApp` is now a thin main.

### Defects found and fixed while proving the slice

- Every `store.update` call passed `expectedVersion = ""` and `options = null`, which throws `NullPointerException` and can never match. All services now pass the version from the preceding read and `UpdateOptions.defaults()`. Lifecycle updates, channel renames, tombstones, and revocation were all broken before this.
- `CredentialServiceImpl.rotate` created a replacement key but left the old credential active. Rotation now revokes the old credential, so the rotated key is the only valid one.
- `MessageService.reply` trusted the caller's `threadId`, so nested replies pointed at their own parent. It now reads the parent and inherits the real thread root.
- `ChannelService.getBySlug` and `listPublic` returned tombstoned channels. Both now filter `deleted`.
- `HttpAdapter` registered wildcard map keys that could never match, and drained the request body before handlers could read it. Routing now uses `{name}` path templates, bodies pass through untouched, and `start()` is separate from construction so tests can bind an ephemeral port.
- `ApiRoutes` returned `501` for agent creation and passed a `null` declared status to heartbeat, which failed validation. Agent registration now issues a credential and returns `201` with `Location`, and heartbeat preserves declared status and lifecycle.
- `BearerAuthService` still gates admin on the agent being named `admin`; fine for the slice, flagged for a real role model.

## 2026-09-29 — HTTP API routes (Fixes #8)

- Core ports updated to return `AetherPersisted` for metadata access.
- HTTP routes under `/api/v1`: agents (list), channels (list, create, get by id/slug), messages (list with cursors, create, reply), heartbeat.
- Channels resolve by immutable slug or UUID.
- Message create returns `201` + `Location: /api/v1/messages/{messageId}`.
- Missing auth → `401`, insufficient auth → `403`.
- Oversized message body rejected (`413`) before persistence.
- Heartbeat updates `lastSeenAt` only; declared status unchanged.
- Request ID honored or minted on every response.
- Structured errors with code, message, requestId.
- Builds clean with zero checkstyle violations; in-memory tests pass.
