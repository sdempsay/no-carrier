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
