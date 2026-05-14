# CLAUDE.md

Orientation for Claude Code sessions in this repo.

- **What this repo is:** a dev harness for the Swiss GovTech Hackathon
  2026 VACD challenge. `README.md` has the overview, service table, and
  architecture patterns.
- **Chronological state and next steps:** `progress/main-progress.txt`
  (or the current branch's progress file). Update it via the
  `update-progress` skill.

## Scope

The harness = `.devcontainer/`, `docker-compose.yml`, `services/` source
for locally-built containers, and `docs/`. The **glue service** that
mediates FHIR ⇄ openEHR is what hackathon participants build — it is
deliberately not part of this repo, and `docker-compose.yml` must not
contain it.

## Constraints

- **Lean discipline:** dev container image <4 GB, total compose working
  set ~4 GB. Participants pull this over conference wifi, so push back
  on anything heavy.
- **Step-by-step on compose:** don't add services speculatively. Wait
  until the user provides source or context for a service before wiring
  it in.
