# Platform Business Logic Example

Minimal smoke-test used to validate that the harness backing services
(FHIR server, openFHIR, EHRbase) work together end-to-end. Implements
a write path in Kotlin/Ktor.

> **This is not a starting point or template.** Hackathon participants
> should design and build their own platform layer from scratch, in
> whatever language and shape they prefer. This module exists only to
> prove the plumbing works. It is behind the `example` compose profile
> and does **not** start with a plain `docker compose up`.

## Run

**Option A — via Docker Compose profile** (recommended):
```bash
docker compose --profile example up --wait
# then open http://localhost:8888/demo
```

**Option B — from source** (useful during development):
```bash
# make sure the backing services are up first:
#   docker compose up --wait

cd services/platform-business-logic-example
./gradlew --no-daemon fatJar
java -jar build/libs/platform-business-logic-example-0.1.0-all.jar
```

All connection URLs and credentials are pre-set as env vars by the dev
container (`FHIR_SERVER_1_URL`, `MAPPER_URL`, `CDR_URL`, `CDR_USER`,
`CDR_PASS`) and as defaults in `application.conf` — no manual exports
needed.

Then open <http://localhost:8888/demo> in a browser.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/Immunization` | Ingest a CH VACD Immunization Administration document Bundle (strict — non-document Bundles return 400). |
| `GET`  | `/metadata` | Minimal CapabilityStatement |
| `GET`  | `/examples` | List the canonical example Bundles |
| `GET`  | `/examples/{slug}` | Fetch a specific canonical Bundle |
| `GET`  | `/healthz` | Liveness — dependency probes + bootstrap state |
| `GET`  | `/demo` | Three-panel HTMX demo UI |
| `POST` | `/demo/convert` | UI handler — ingests the textarea contents and renders the result |
| `GET`  | `/demo/example/{slug}` | UI handler — loads a canonical Bundle into the textarea |

## Tests

```bash
# unit tests only
./gradlew test

# include the end-to-end integration suite (hits the live compose stack)
INTEGRATION=1 ./gradlew test
```

## Demo presentation

A landscape PDF deck rendered from Playwright screenshots lives at
[`docs/demo/ch-vacd-pattern-a-tracer-bullet.pdf`](../../docs/demo/ch-vacd-pattern-a-tracer-bullet.pdf).
Regenerate it with:

```bash
cd services/platform-business-logic-example/playwright
node run.mjs        # capture screenshots into docs/demo/shots/
python3 make_pdf.py # build docs/demo/ch-vacd-pattern-a-tracer-bullet.pdf
```
