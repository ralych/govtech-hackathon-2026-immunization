# GovTech Hackathon 2026 — Digital Immunization Record (VACD)

Dev harness for the Swiss GovTech Hackathon 2026 **VACD** challenge
(28–29 May 2026, FOITT Zollikofen).

Clone this repo, open it in VS Code with the Dev Containers extension, and
`docker compose up`. You get a fixed set of backing services that the
challenge revolves around. **Your team writes the glue service** that
mediates between the CH VACD FHIR API and an openEHR Clinical Data
Repository. The glue is *not* part of this harness — that's the challenge.

## What's in the harness

| Service          | Port  | What it is                                                                                       |
| ---              | ---   | ---                                                                                              |
| `fhir-server-1`  | 9111  | CH VACD FHIR API reference server, instance 1 (HAPI FHIR JPA, Spring Boot, Java 21, H2 in-memory) |
| `fhir-server-2`  | 9112  | Same image, instance 2 (separate JVM and in-memory H2)                                            |
| `ehrbase`        | 8082  | openEHR Clinical Data Repository (Postgres-backed)                                                |
| `openfhir`       | 8083  | FHIR ⇄ openEHR mapping engine (Mongo-backed)                                                      |

The two FHIR servers are intentionally generic — *the harness does not
assign them producer/consumer roles*. How you use them depends on which
architecture pattern you pick (below).

Source for `fhir-server-{1,2}` is vendored at
`services/ch-vacd-api-reference-server/` (Roeland's reference implementation,
no inner `.git`). The other services are pulled from upstream images.

## Architecture options (open by design)

The harness deliberately doesn't pick a glue topology — call this
**Pattern D**, where all backing services run default-on and your team
decides how to wire them. Three concrete patterns we expect to see:

- **Pattern A — Glue as FHIR server.** Your glue *exposes* the CH VACD
  FHIR API itself. openFHIR + EHRbase sit behind it as storage. The two
  reference-server instances are **not in the runtime path** — use them
  only as a spec implementation to validate your responses against.

- **Pattern B — Glue as ETL bridge.** Treat `fhir-server-1` as the
  upstream (producer side) and `fhir-server-2` as the downstream
  (consumer side). Your glue reads from server-1, transforms via
  openFHIR, stores in EHRbase, transforms back, writes to server-2. The
  glue itself does *not* expose a FHIR API.

- **Pattern C — Glue as facade.** Hybrid of A and B. Your glue exposes
  some API and delegates storage; EHRbase is the canonical form behind
  it.

You don't need to declare a pattern at the start — pick whatever shape
gets you to a working end-to-end flow fastest, given your team's stack.

## Producer and consumer apps

End-to-end the challenge has three pieces, not one:

- A **producer** that emits a **CH VACD Immunization Administration
  Document** (a clinician documenting an administered dose).
- The **glue / orchestration** that lands it in openEHR and serves it
  back out.
- A **consumer** that fetches and renders a **CH VACD Vaccination
  Record Document** (a patient or care provider viewing the record).

Both ends are wide open — a clinician web form, a CLI that posts a
canned bundle, a mobile-style patient view, a printable summary,
whatever fits the demo you want to give. Be creative; the interesting
judging surface is as much *what you do with the data* as the mapping
itself. The harness only provides the FHIR + openEHR backing services;
the producer, consumer, and the glue between them are all yours.

## Running the harness

Inside the dev container (open the repo in VS Code → "Reopen in Container"):

```sh
docker compose up
```

First-time build pulls dependencies (HAPI FHIR + Spring Boot) and takes a
few minutes. Subsequent builds use Maven's cache mount and are near-instant
when sources are unchanged.

Verify both FHIR servers respond:

```sh
curl http://host.docker.internal:9111/ch-vacd-api-reference-server/fhir/metadata
curl http://host.docker.internal:9112/ch-vacd-api-reference-server/fhir/metadata
```

Both should return a `CapabilityStatement` (HAPI FHIR 8.8.1, FHIR R4).

`host.docker.internal` is the right hostname from inside the dev container
(your glue code uses the same; see `FHIR_SERVER_1_URL` / `FHIR_SERVER_2_URL`
/ `CDR_URL` / `MAPPER_URL` in `.devcontainer/devcontainer.json`).

## Building your glue service

The harness reserves a range of host ports (3000, 3001, 8000, 8001, 8080,
8081) for your glue. Pick whichever your stack defaults to — they're all
forwarded by the dev container without any extra config. Run your glue
inside the dev container terminal; hit it from your browser/Postman on
your host machine like any localhost service.

## What's in this repo

```
.devcontainer/   dev container config — Java 25 + Maven/Gradle, Node 22,
                 Python 3.13, Kotlin 2.3, Docker, gh, Claude/Gemini/Codex
docker-compose.yml             the backing services
services/        source for services built from local Dockerfiles
  ch-vacd-api-reference-server/  HAPI FHIR JPA reference server
docs/
  challenge/     the original hackathon brief
  bootstrap/     openEHR + openFHIR setup notes (in progress)
```

## Lean-discipline notes

Participants pull this over conference wifi. Targets:

- Dev container image **< 4 GB**.
- Total compose working set **~4 GB** (fits a 16 GB laptop with room).

If you find yourself adding heavy images, check first.
