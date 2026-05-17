# PBLL — Platform Business Logic Layer

Reference implementation of a Pattern A tracer bullet for the CH VACD
Immunization write path. See
[`docs/architecture/patterns.md`](../../docs/architecture/patterns.md)
for the pattern definition.

> **Not a harness service.** The PBLL is what hackathon participants build.
> This module is a reference example and is **not** wired into
> `docker-compose.yml` (see [`CLAUDE.md`](../../CLAUDE.md)).
> Run it from the dev container with `java -jar` — both sides share the
> `vacd-net` bridge so service-name URLs Just Work.

## Run

```bash
# from the repo root
cd services/pbll
./gradlew --no-daemon fatJar

FHIR_SERVER_1_URL='http://fhir-server-1:9111/ch-vacd-api-reference-server/fhir' \
MAPPER_URL='http://openfhir:8083' \
CDR_URL='http://ehrbase:8080/ehrbase/rest/openehr/v1' \
CDR_USER='ehrbase-user' \
CDR_PASS='SuperSecretPassword' \
java -jar build/libs/pbll-0.1.0-all.jar
```

Then open <http://localhost:8080/demo> in a browser.

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
PBLL_INTEGRATION=1 ./gradlew test
```

## Demo presentation

A landscape PDF deck rendered from Playwright screenshots lives at
[`docs/demo/ch-vacd-pattern-a-tracer-bullet.pdf`](../../docs/demo/ch-vacd-pattern-a-tracer-bullet.pdf).
Regenerate it with:

```bash
cd services/pbll/playwright
node run.mjs        # capture screenshots into docs/demo/shots/
python3 make_pdf.py # build docs/demo/ch-vacd-pattern-a-tracer-bullet.pdf
```
