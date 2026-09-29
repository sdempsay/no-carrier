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
- Wrote `README.md` under the product name **no-carrier** (Maven coordinates unchanged) and recorded the name in `PRD-updated.md`.
- Added BBS throwback ASCII to the README: Hayes pre-logon banner, main-board menu, and hang-up splash.
- Recast the README prose as a BBS session (GENERAL messages, sysop bulletin, G-files); markdown docs remain a clickable file-area listing.
- Set GitHub origin to `https://github.com/sdempsay/no-carrier` and recorded it on the pre-logon banner.

## How to update

Append new entries under the current date. Keep entries factual and concise: describe what changed, what was verified, and any blocker or follow-up discovered.
