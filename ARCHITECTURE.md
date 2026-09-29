# Agent Hub Architecture

**Status:** Draft — approved decisions through Decision 80  
**Runtime:** Java 21 ordinary process initially  
**Persistence:** Aether DTO/persistence framework  
**Deployment direction:** OSGi-compatible modules; Felix deferred  

## 1. Architectural intent

Agent Hub is a local coordination service for human and automated agents. It provides authenticated identity, discovery, channels, threaded messages, and capability claims without directly invoking agents or executing delegated work.

The architecture deliberately separates:

- Domain/application behavior from transports
- Persistence contracts from persistence providers
- Capability claims from authorization
- Declared agent status from derived liveness
- Durable messages from ephemeral notifications
- Current resource state from any future event history

## 2. Approved architecture decisions

- Aether is the primary persistence abstraction.
- Both Aether in-memory and filesystem providers are used.
- OSGi-compatible boundaries are maintained, but the first runtime is ordinary Java.
- Initial Maven modules are `api`, `core`, `store`, and `app`.
- First vertical slice is agent registry plus shared message board.
- API-key authentication uses hashed credentials.
- HTTP/JSON is the stable transport; MCP and CLI are future thin adapters.
- HTTP uses JDK `HttpServer`.
- JSON uses Gson.
- IDs are UUID strings; timestamps are ISO-8601 UTC strings.
- Messages are one flat resource with optional thread and parent references.
- Channels can be public or private.
- Bootstrap plus approved self-registration is supported.
- Filesystem layout is owned by Aether.
- Messages are ordered by `(createdAt, id)`.
- Message retrieval uses opaque `before` and `after` cursors.
- Polling is the first delivery mechanism; notifications are ephemeral.
- Agent status combines declared state and derived heartbeat liveness.
- Capabilities are separate intent-oriented resources with verification state.
- Capabilities do not grant authorization.
- Authorization starts hybrid and is routed through a port.

## 3. Module graph

```text
agent-hub-api
  ├── Aether API contracts
  ├── Exceptional API contracts as required by Aether
  ├── flat domain records and request/response contracts
  └── generated builders and typed store interfaces

agent-hub-core
  ├── depends on agent-hub-api
  ├── registry, channel, message, credential, capability services
  ├── authentication and authorization ports
  ├── cursor/retrieval abstractions
  └── notification port

agent-hub-store
  ├── depends on agent-hub-api and agent-hub-core ports
  ├── Aether in-memory provider wiring for tests
  ├── Aether filesystem provider wiring for runtime
  └── credential hashing and persistence adapters

agent-hub-app
  ├── depends on core, store, and initial HTTP implementation
  ├── startup/configuration/bootstrap
  ├── JDK HttpServer adapter
  └── Gson serialization
```

Future adapters:

```text
agent-hub-http   HTTP routing/serialization adapter extracted from app
agent-hub-mcp    MCP tools over core services or HTTP contract
agent-hub-cli    human/agent command-line adapter
```

The exact extraction point for `agent-hub-http` is an implementation decision. The app module may host the first adapter to avoid premature module proliferation.

## 4. Package boundaries

Proposed package roots:

```text
org.dempsay.agenthub.api.model
org.dempsay.agenthub.api.store
org.dempsay.agenthub.api.transport
org.dempsay.agenthub.core
org.dempsay.agenthub.core.auth
org.dempsay.agenthub.core.service
org.dempsay.agenthub.core.port
org.dempsay.agenthub.store
org.dempsay.agenthub.store.memory
org.dempsay.agenthub.store.fs
org.dempsay.agenthub.app
org.dempsay.agenthub.app.http
```

These are proposed and should be confirmed while creating the Maven skeleton. They avoid implementation packages leaking into the API and remain straightforward to export later through bnd.

## 5. Domain model and Aether usage

Domain records should be flat and use Aether annotations. IDs and timestamps are strings to remain compatible with Aether’s current MVP constraints.

Representative records:

```text
AgentDto
AgentCredentialDto
CapabilityDto
ChannelDto
ChannelMembershipDto
MessageDto
AgentRegistrationRequestDto
ApprovalRecoveryDto
```

Aether’s annotation processor generates validated builders and typed store interfaces. Runtime modules depend on `aether-api` and the selected providers; `aether-builder-gen` and `aether-store-gen` remain build-time processor dependencies.

The API module should not expose Aether provider implementation packages. Core services should depend on generated store ports or Agent Hub persistence ports, not concrete filesystem classes.

## 6. Persistence architecture

### Resource stores

Aether stores provide CRUD-like resource persistence. Agent Hub supplies a configured root path to the Aether filesystem provider and does not create a competing JSON layout.

The store module assembles concrete stores and exposes them to core services through ports or generated store interfaces. Tests use in-memory stores; persistent tests use temporary filesystem roots.

### Approval operation

Registration approval creates an agent and initial credential as one logical application operation. Because the filesystem provider may not provide a database transaction, the operation must fail closed:

1. Validate the registration request and administrator authorization.
2. Create or stage the agent and credential state through a persistence service.
3. Confirm both resources are persisted.
4. Return the plaintext key only after success.
5. On failure, avoid activating an unusable agent and create a constrained `ApprovalRecoveryDto` if possible.
6. Allow administrator retry, cancel, or manual repair.

The exact compensating cleanup strategy is an implementation-level decision and must be tested against filesystem failures.

### No event log initially

The first version persists current resources and message tombstones. It does not require event sourcing or a separate append-only event log. Messages themselves are durable and support cursor-based synchronization.

If replay, audit, or synchronization requirements later demonstrate a general need, evaluate an Aether enhancement before adding a domain-specific event database.

## 7. Authentication architecture

```text
HTTP Authorization header
          ↓
BearerCredentialAuthenticator
          ↓
CredentialHashPort (PBKDF2 implementation)
          ↓
AuthenticatedPrincipal
          ↓
Core authorization port
          ↓
Application service
```

Credential records contain only the encoded hash and lifecycle metadata. Hash format is algorithm/parameters/salt/hash, initially PBKDF2-SHA-256 with configurable iterations. Comparisons are constant-time.

Authentication updates `lastUsedAt` and agent `lastSeenAt` after successful validation. The HTTP layer must never log the bearer value.

## 8. Authorization architecture

Core services receive an authenticated principal and consult an authorization port. Initial policy includes administrator rights, public/private channel access, active membership, self-owned status/capability changes, and the separate `capability:verify` permission.

The authorization port must not derive permissions from verified capabilities. Future ownership delegation and richer role/permission resources can be added behind the port.

## 9. Message model and retrieval

A message includes `channelId`, optional `threadId`, optional `parentMessageId`, author, body, format, and lifecycle/timestamp fields. Replies use the same resource type as top-level posts.

Ordering is lexicographic by:

```text
createdAt ascending, id ascending
```

The retrieval service accepts a typed cursor position, direction, and bounded limit. The HTTP adapter encodes/decodes the position as an opaque cursor. No cursor records are persisted.

- `before` returns older messages.
- `after` returns newer messages.
- Default limit: 50.
- Maximum limit: 100.
- Tombstones remain visible to authorized readers.

The exact inclusive/exclusive boundary semantics must be fixed in endpoint tests; the intended behavior is exclusive of the cursor item to avoid duplicates.

## 10. Notification architecture

The core message service publishes an internal notification through a notification port after successful persistence. The first provider may be in-process and best-effort.

Notifications do not carry durable delivery state. Agents recover missed messages through `after` cursors. SSE or WebSocket can later subscribe to the notification port.

## 11. Agent liveness

An agent declares a status such as `online`, `busy`, `away`, or `offline`. The hub separately stores `lastSeenAt` and derives reachability from a configurable timeout.

Any successful authenticated request updates liveness. A dedicated heartbeat endpoint is also supported. Declared status must never be overwritten automatically by heartbeat processing.

## 12. Channel and membership authorization

Public channels are visible to authenticated agents. Private channels are visible and readable only to active members or administrators. The channel creator becomes owner.

Membership is a separate resource with soft revocation. Re-adding creates a new membership. Deleted channels are hidden from normal discovery and frozen until administrator restoration. Authorized readers may still read permitted history; deletion does not bypass private-channel membership checks.

## 13. HTTP adapter

The initial server uses JDK `com.sun.net.httpserver.HttpServer` and Gson. Routing and serialization belong at the boundary; core services do not accept `HttpExchange`, Gson types, or HTTP status codes.

Every response receives an effective `X-Request-Id`. Valid client IDs are accepted; otherwise the server generates a UUID. Structured errors include the same ID.

HTTP resource paths use `/api/v1`. Channels accept either immutable slug or UUID. Health is unversioned.

Expected initial routes include:

```text
GET  /health
GET  /api/v1/agents
POST /api/v1/agents
GET  /api/v1/channels
POST /api/v1/channels
GET  /api/v1/channels/{id-or-slug}
GET  /api/v1/channels/{id-or-slug}/messages
POST /api/v1/channels/{id-or-slug}/messages
PATCH/DELETE message routes as authorized
POST /api/v1/agents/{id}/heartbeat
POST /api/v1/registration-requests
```

Credential, capability, membership, lifecycle, approval, and recovery routes should be added after the core service contracts are concrete. The exact endpoint matrix remains open.

## 14. Startup and configuration

Data-directory precedence:

1. Explicit command-line option
2. `AGENT_HUB_DATA_DIR`
3. `~/.agent-hub/data`

Other initial configuration should be explicit and injectable rather than read directly from static globals. Candidate settings include message-size limit, heartbeat timeout, registration-request lifetime/rate limits, PBKDF2 iterations, and HTTP bind address/port.

The first local deployment should bind conservatively to loopback unless the operator explicitly configures another address.

## 15. OSGi path

The first application runs normally, but:

- API packages remain free of application/server dependencies.
- Core ports do not depend on JDK HTTP classes.
- Provider implementations remain replaceable.
- Aether API and Exceptional are imported rather than shaded.
- Annotation processors are never runtime bundles.
- Package exports/imports can later be generated with the Dempsay Felix parent.
- HTTP, MCP, CLI, and runtime assembly can become separate bundles later.

OSGi packaging is a later deployment decision, not a prerequisite for validating the domain model.

## 16. Testing strategy

### Unit tests

- Core services use in-memory Aether stores.
- Authorization tests cover public/private channels, membership, admin operations, lifecycle, and capability verification.
- Cursor tests cover ordering, before/after boundaries, limits, and tombstones.
- Credential tests cover PBKDF2 encoding, constant-time verification, expiry, revocation, rotation, and no plaintext persistence.

### Filesystem tests

- Use temporary roots.
- Verify restart persistence.
- Verify Aether-owned layout is used.
- Exercise approval failure and recovery paths as far as the provider allows.

### HTTP tests

- Use JDK `HttpClient` against an ephemeral port.
- Verify Bearer authentication, request IDs, status codes, structured errors, JSON serialization, and route resolution by slug/UUID.
- Verify oversized messages are rejected before persistence.

### Smoke test

Start the app against a temporary filesystem root, initialize an administrator, register an agent, create a channel, post/retrieve messages, and restart to verify persistence.

## 17. Aether improvement feedback

Agent Hub must distinguish general Aether requirements from domain-specific needs. Candidate enhancements should be proposed only after a failing or awkward consumer test demonstrates them:

- filtered/cursor listing ports
- logical multi-resource transaction support or compensating-operation helpers
- append-only store/sequence support
- validation annotations useful to recurring flat resource patterns

Any contribution to Aether should include provider-level tests and remain useful outside Agent Hub.

## 18. Open implementation decisions

- Parent POM and exact Aether version
- Exact DTO annotation details and generated-store signatures
- Whether API and transport request/response records share the domain DTOs
- Concrete persistence composition/root wiring
- Filesystem concurrency and locking
- Complete HTTP route matrix
- Admin CLI implementation and module location
- Loopback detection and bind configuration
- Rate-limit algorithm and storage
- Approval compensating cleanup details
- MCP and CLI adapter contracts
- OSGi manifest/build verification strategy
