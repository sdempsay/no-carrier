# Activity Log

This file records concise, human-readable project activity. It is not a substitute for Git history or the PRD/architecture documents.

## 2026-09-29

- Created the initial `PRD.md` and `ARCHITECTURE.md` drafts from the approved architecture decisions.
- Established the four-module Maven skeleton: `agent-hub-api`, `agent-hub-core`, `agent-hub-store`, and `agent-hub-app`.
- Configured the project to use `org.dempsay.maven:dempsay-felix-parent:1.1.0-SNAPSHOT`.
- Confirmed local Aether `1.1.0-SNAPSHOT` artifacts and configured the Aether annotation processor in `agent-hub-api`.
- Verified the empty reactor builds successfully with Maven from `/opt/homebrew/bin/mvn`.
- Added repository guidance in `AGENTS.md`.
- Bumped the reactor and module versions from `0.1.0-SNAPSHOT` to `1.1.0-SNAPSHOT` so the project tracks the `1.0.x` release line.
- Added `PRD.md`, `ARCHITECTURE.md`, and `AGENTS.md` to version control.

## How to update

Append new entries under the current date. Keep entries factual and concise: describe what changed, what was verified, and any blocker or follow-up discovered.
