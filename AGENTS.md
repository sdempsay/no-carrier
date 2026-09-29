# Agent Instructions

## Project

Agent Hub is a local, authenticated coordination hub for human and automated agents. The project is Java 21-oriented, uses Aether for persistence, and is being designed for eventual OSGi deployment while initially running as an ordinary Java application.

## Current architecture decisions

- Use `dempsay-felix-parent` as the Maven parent.
- Keep OSGi-compatible module and package boundaries, but do not require Felix for the initial runtime.
- Use the four initial Maven modules:
  - `agent-hub-api`
  - `agent-hub-core`
  - `agent-hub-store`
  - `agent-hub-app`
- Use Aether in-memory stores for tests and Aether filesystem stores for persistent local operation.
- Use JDK `HttpServer` and Gson for the initial HTTP/JSON adapter.
- Use API keys with hashed credentials; never persist or log plaintext keys.
- Keep authentication, authorization, persistence, and transport concerns behind replaceable ports where practical.
- Treat capabilities as descriptive claims; capabilities do not grant authorization.
- Do not introduce an event log unless a concrete requirement demonstrates the need. Consider generally useful Aether improvements before adding Agent Hub-specific persistence workarounds.

## Repository workflow

1. Read `PRD.md`, `ARCHITECTURE.md`, and this file before making structural changes.
2. Read `ACTIVITY.md` before starting work and append a concise entry after meaningful changes.
3. Use the local Maven installation at `/opt/homebrew/bin/mvn` when `mvn` is not on `PATH`.
4. Prefer Java 21 for builds and verify the active Java version before relying on newer language or API features.
5. Run targeted tests and a Maven build after meaningful changes.
6. Verify OSGi bundle headers for bundle modules when packaging changes.
7. Do not modify Aether or parent-project files from this repository; make changes in their repositories separately and document the consumer need here.

## Code conventions

- Keep API records flat where required by Aether's current DTO limitations.
- Use UUID strings for identifiers and ISO-8601 UTC strings for timestamps.
- Keep HTTP types and Gson types out of core services.
- Keep provider implementations out of API contracts.
- Prefer `ExceptionalResponse` and existing Aether failure conventions for expected failures.
- Preserve tombstones and historical attribution for deleted messages, channels, agents, credentials, memberships, and capabilities where the PRD requires it.

## Validation command

From the repository root:

```bash
PATH="/opt/homebrew/bin:$PATH" mvn -DskipDocker package
```

During scaffolding, project-wide style checks may be skipped only when necessary to isolate compilation or dependency issues; do not treat a skipped check as a passing quality gate.
