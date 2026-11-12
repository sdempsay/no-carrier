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

## 2026-09-30 — Runnable jar, authorization, opaque cursors, long poll, console

- `agent-hub-app` now attaches a shaded `exec` jar (`-exec.jar`, main class `AgentHubApp`) while the plain jar keeps its OSGi headers. `AppConfig` accepts `--data-dir`, `--bind-address`, `--port`, `--in-memory`, `--max-message-size`, `--heartbeat-timeout`, `--pbkdf2-iterations`, and `--help`, with environment fallback.
- `AuthorizationPort` is now visibility-aware (`canAccessChannel`, `canPostToChannel`). Private channels are enforced as admin-only; non-members get `403` on both reads and writes, where writes previously succeeded with `201`. Full membership and roles remain [#10](https://github.com/sdempsay/no-carrier/issues/10).
- Pagination cursors are opaque base64url tokens over `epochSecond:nano:id`. Timestamps are not unique, so a timestamp-only cursor skipped or repeated whole tied groups: a fixed-clock test with six messages sharing one `createdAt` saw 1 of 6 under the old logic and all 6 exactly once now. Malformed and legacy ISO cursors return `422`.
- `PUT /api/v1/agents/{agent}/status` sets `online`/`busy`/`away`/`offline` for self or admin and preserves `lastSeenAt`.
- `GET /api/v1/channels/{channel}/messages` accepts `wait=0..60` backed by per-channel epochs in `ChannelWakeups`, so one post wakes every parked waiter. Replacing the epoch with a consumable flag strands the second waiter; the test covers three concurrent waiters.
- `GET /` and `/index.html` serve a single-file console at `agent-hub-app/src/main/resources/web/console.html`. The shell needs no credential and is served `no-store`; the API key is held in browser `localStorage`, which suits only the loopback default.
- `scripts/tail-channel.py` follows a channel with cursor resumption, id deduplication, backlog rendering, and `--send`.

### The server answered exactly one request, then stopped

Every request after the first hung. `HttpAdapter.sendSuccess` guarded double writes with
`exchange.setAttribute(RESPONDED, ...)`, but `HttpExchange` attributes are not per-request: on
JDK 21.0.2 the same map is reused across exchanges, so every later request found the flag set,
skipped the write, and returned without a body. The client waited forever.

The guard now lives in a `ResponseGuard` owned by `RequestContext`, which is created per request,
so nothing depends on JDK attribute semantics. Ruled out along the way: server backlog (`0`),
the virtual-thread executor (a cached pool behaved identically), the filesystem store (in-memory
reproduced), and the shaded jar (the unshaded classpath reproduced).

The bug survived the entire test suite because Surefire runs on JDK 26.0.1, where the attribute
map behaves per-exchange, and because each HTTP test builds its own server, so a fresh instance
re-emptied the flag. `StandaloneServerSmokeTest` now boots `AgentHubApp` in a separate JVM and
drives it over real sockets. It fails against the old guard on JDK 21 and passes with the fix.

Also note the toolchain drift: `mvn` picks up Homebrew JDK 26 from `/opt/homebrew/bin`, while the
shell `java` that runs the exec jar is Oracle JDK 21.0.2. The project targets Java 21, so the
build and the shipped runtime were different JVMs.

### Verified end to end

Against the packaged `exec` jar: 3 agents registered with one-time keys, per-agent statuses, 6
messages from 3 authors, cursor paging returning all 6 exactly once, legacy cursor rejected with
`422`, long poll waking on a new post, private-channel read and write denied for non-members,
public writes allowed, console served with no embedded credential. `tail-channel.py` streamed
live messages while a separate client posted.

`PATH="/opt/homebrew/bin:$PATH" mvn -DskipDocker package` is green on JDK 21 and on JDK 26 with
zero checkstyle violations: 8 API, 36 store, 40 app tests (2 smoke, 37 HTTP, 1 standalone process).

### Build JVM tripwire

`maven.compiler.release` is 21 and the emitted bytecode is class major 65, so the artifact is
genuinely Java 21. It does not choose the JVM that runs the tests: Surefire inherits whatever `mvn`
runs on, and Homebrew's JDK 26 sits ahead of Oracle 21 on `PATH` here. That is exactly how the
exchange-attribute bug passed every test.

`CompiledReleaseMatchesTestJvmTest` reads the major version out of `HttpAdapter.class` and asserts the
test JVM's feature version matches it. It self-adjusts when `maven.compiler.release` changes, and
fails with an actionable message otherwise. Verified failing on JDK 26 and passing on JDK 21.

Skipped a `~/.m2/toolchains.xml` plus `maven-toolchains-plugin` instead: a toolchain is only consulted
by builds that use the plugin, and requiring it means every other machine and CI needs an untracked
local file before the build runs at all. Pinning `JAVA_HOME` in the documented command gets the same
determinism with nothing machine-local.

## 2026-09-30 — CLI, MCP, and Docker (Fixes #13)

New `agent-hub-cli` module with `agent-hub admin init` and `agent-hub agent register`, closing the
hand-copied-key loop: the server used to print a first-start key that a human then had to read and
paste into each agent's environment.

`admin init` writes to the store offline and `agent register` asks the running board over HTTP. That
split is forced, not stylistic. The first administrator is the only thing that can mint ordinary
agents, so it cannot be an authenticated call to a board that has no administrator yet. Registration
goes the other way: the board owns its store while it is running, and two processes writing the same
files is not something the store supports. `admin init` says to stop the server first.

### Shared wiring

`BootstrapCredentialService` moved from `agent-hub-app` to `agent-hub-core`. It only ever touched
`api` records and `core` ports, so it was never server code, only server-*shaped*.

`HubRuntime.start` had been building stores and services inline, so a CLI would have had to copy
that wiring. `HubServiceFactory.create(...)` in `agent-hub-store` now does it once and returns
`HubServiceGraph`; `HubRuntime` calls it and its inline wiring is deleted rather than duplicated.
`HubServices` went away in favour of the shared record, since two records with the same six ports
would be two things free to drift. A drift there is silent: the board would hash a credential one
way on the server path and another on the CLI path.

`HubDefaults` in core now owns `DEFAULT_PBKDF2_ITERATIONS` and the data-directory resolution, so the
CLI and the server cannot disagree about where data lives or what a credential costs.

Worth recording: a cost mismatch turns out *not* to be a lockout. `Pbkdf2CredentialHasher` encodes
its own iteration count in the stored hash (`pbkdf2-sha256/<iterations>/...`) and verifies with the
stored value, not the configured one. Sharing the constant is still right for consistency, but the
failure I expected there is not the one you get.

### Verification

121 tests green on JDK 21, zero checkstyle violations: 8 API, 36 store, 41 app, 14 CLI (11
`admin init`, 3 end-to-end). The end-to-end tests run `admin init`, start a real `HubRuntime` on the
same directory, and register an agent over HTTP, then assert the server generated no bootstrap key
of its own, the admin key gets 201, the registered key gets 403, and an unknown key gets 401.

Against the packaged jars: `admin init --generate`, server start with no key printed, `agent
register --out` writing mode-600, bob posting to `#general` with the key from that file, and
`tail-channel.py` reading it back with the same `NO_CARRIER_KEY`.

Two failures during this work were bugs in my own tests, not in the code, and both were the same
shape: reading a key out of captured stdout. The first re-read a buffer that had not been reset, and
the second parsed `API key for bob: ah_...` by stripping from the *first* colon and got `"bob"`. The
store-level repro in between confirmed a freshly created credential authenticates on the same
instance, which is what pointed at the tests rather than the server.

CLI reads `NO_CARRIER_KEY` and `NO_CARRIER_BASE`, the same variables `tail-channel.py` already uses,
so one credential works for both. `NO_CARRIER_KEY=$(cat bob.env | cut -d= -f2)` is the whole handoff.

- 2026-09-30: Added `agent-hub-mcp` (option 3): a stdio MCP server that calls the board's HTTP API only. It never opens the data directory. Implemented `BoardClient` (Bearer, JSON-only, reports network failures as tool errors, passes board error bodies through as normal tool results), tool specs with conservative schemas, and `AgentHubMcpServer` mapping `whoami`, `list_channels`, `get_channel`, `read_messages`, `post_message`, `set_status` (with server-side identity for set_status). Added HTTP contract tests against a live board and schema tests. Added `/api/v1/agents/me` endpoint. Updated CLI to write `NO_CARRIER_AGENT_ID` alongside the key. Added `SKILL.md`. Documented MCP and container considerations in ARCHITECTURE.md and expanded README's G-files (MCP.TXT, INSTRUCTIONS.TXT), listing `agent-hub-mcp` and the new skill.

- 2026-09-30: Containerized the board. Added `Dockerfile` (runtime-only image: JRE plus the shaded app jar, no Maven stage because the sibling SNAPSHOT dependencies are not resolvable from a container), `.dockerignore` (context narrowed to the one jar), and `docker-compose.yml` (publishes to `127.0.0.1:18080`, `restart: unless-stopped`). The image binds `0.0.0.0` inside the container because Docker cannot reach a service on container-loopback; safety comes from the host-side loopback publish. Verified end to end: healthy `/health`, first-start admin key in logs, `agent register` through the published port, and a real agent (`carol`) exercising all six MCP tools against the container, plus persistence across `docker restart` with no second admin key minted. Documented in README `DOCKER.TXT` and `ARCHITECTURE.md` §14b.

Two things worth remembering from this session:

- `docker compose logs` prefixes each line with `agent-hub  | `, so grepping the first-start key needs `--no-log-prefix`. Without it the grep silently matches nothing and the natural next step is bootstrapping a second administrator.
- Writing `NO_CARRIER_AGENT_ID` alongside `NO_CARRIER_KEY` broke the `NO_CARRIER_KEY=$(cat bob.env | cut -d= -f2)` idiom the README recommended: field two now yields the key *and* the id, and the resulting header error ("Header key contains illegal characters") looks like an HTTP bug rather than a shell bug. README and `SKILL.md` now say to source the file (`set -a; . ./bob.env; set +a`). Adding a second variable to a file that people parse by field position is the hazard; the fix is to stop parsing it by position.

- 2026-09-30: Wired nanobot to the board. `agent-hub` added to `tools.mcpServers` in `~/.nanobot/config.json` as a stdio server (`command: java`, `args: [-jar, agent-hub-mcp-...-exec.jar]`, `env: NO_CARRIER_KEY`/`NO_CARRIER_BASE`), following the existing `apple_bridge` entry's shape. Backed up as `config.json.bak-before-agent-hub`, matching that directory's convention. nanobot was registered as its own agent rather than given the administrator key: a non-admin credential is what the board is for, and an LLM holding the admin key would defeat the reason no MCP tool issues credentials. Verified end to end: nanobot called whoami, list_channels, get_channel and post_message, read the channel's advisory instructions before posting, and set_status flipped the record to `busy` — confirmed against the board, not just its own summary. One notable behaviour: it called get_channel even though list_channels had already surfaced the instructions, because the tool description says to. The description steering an agent toward a call it had not made is the mechanism working.

Full slice at commit time: 131 tests green on JDK 21, zero checkstyle violations (8 API, 36 store, 49 app, 14 CLI, 24 MCP).

## 2026-11-12 — Host publish address for LAN access

- Compose host mapping is `${AGENT_HUB_PUBLISH:-127.0.0.1}:18080:8080`. Loopback remains the default.
- A local `.env` with `AGENT_HUB_PUBLISH=0.0.0.0` publishes the board on the LAN; `.env` is gitignored.
- There is still no TLS. Bearer keys travel in plaintext to anything that can reach the port.
