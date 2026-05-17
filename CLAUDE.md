# CLAUDE.md

Orientation for Claude Code sessions in this repo.

- **What this repo is:** a dev harness for the Swiss GovTech Hackathon
  2026 VACD challenge. `README.md` has the overview, service table, and
  architecture patterns.

## Scope

The harness = `.devcontainer/`, `docker-compose.yml`, `services/` source
for locally-built containers, `examples/` (canonical CH VACD test
Bundles), and `docs/`. The **platform layer** that mediates between
the CH VACD FHIR API and the openEHR Clinical Data Repository is what
hackathon participants build — it is deliberately not pre-decided by the
harness, and `docker-compose.yml` must not contain it.

The project currently ships **one example platform**, the **PBLL** — short for **Platform
Business Logic Layer** — at `services/pbll/`. It is a *reference*
implementation of Pattern A (a Kotlin/Ktor FHIR facade over openEHR), not
*the* implementation. Teams are expected to write their own, in whatever
language and shape they prefer; the PBLL is there to copy, extend, or
ignore. Do **not** add it to `docker-compose.yml` — participants run it
themselves with `java -jar` from the dev container.

