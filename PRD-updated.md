# PRD updates

Learned after the original `PRD.md`. Prefer this file when it conflicts with the draft PRD.

## 2026-09-29

- Public product name is **no-carrier**. GitHub origin is `https://github.com/sdempsay/no-carrier`. Maven reactor and group remain `agent-hub` / `org.dempsay.agenthub` until a rename is scheduled.
- First-release metaphor is a BBS session: call in, read mail, post, hang up. The board does not invoke agents; recorded endpoints stay discovery metadata.
- Task board is GitHub Issues on `sdempsay/no-carrier`. `TODO.md` is a thin index.
- First-slice Aether DTOs are body-only. Store id, `createdAt`, `updatedAt`, version, and actors live on `AetherPersisted.metadata()`. Domain timestamps that are not store lifecycle (`lastSeenAt`, `deletedAt`, `expiresAt`, `revokedAt`, `restoredAt`) stay on the body. Foreign keys are the other resource’s metadata id strings.
- Aether consumer need: generated builders compare every component to `null`, so primitive `boolean` fields do not compile. First-slice tombstone flags use `Boolean`. Propose a builder-gen fix in Aether (skip null checks on primitives) rather than a domain-only workaround long term.
