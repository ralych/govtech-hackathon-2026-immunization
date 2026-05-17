# Architecture Patterns

Reference document for how the Swiss GovTech Hackathon 2026 VACD harness fits
together at runtime. Two deployment patterns share the same internal stack;
they differ in *where the boundary is drawn* — whether the platform is the
system of record or a mediator between external systems.

Stable file. PRPs (in `docs/prp/`) reference this document rather than
restating its content.

## Components (shared between patterns)

| Service | Role | Why this component |
|---|---|---|
| **PBLL** (Platform Business Logic Layer) | The platform's brain. Receives requests, fans out to storage, exposes helper services, orchestrates conversion. Kotlin + Ktor. | Single place to encode CH VACD-specific routing rules, validation policy, format mediation. Replaces what older notes call "glue service". |
| `fhir-server-1` (HAPI 8.8.1) | **Demographics + directory store.** Patient, Practitioner, Organization, Location, PractitionerRole. CH VACD profile authority. | HAPI has Patient indexing, version history, search-by-name out of the box — none of which EHRbase provides natively. Roeland's 17 CH VACD `ResourceProvider` classes encode the profile rules so we don't reinvent them. |
| `openfhir` (2.2.1) | **Stateless mapping engine** for FHIR ↔ openEHR conversion via FHIRconnect YAMLs. | Lets us declaratively map a CH VACD `Immunization` to an openEHR `Composition` without writing transformation code. Mappings + templates are state; bodies are pure functions. |
| `openfhir-mongo` (Mongo 7.0) | openFHIR's configuration store (templates, contexts, model mappings). | Internal to openFHIR; not addressed by the PBLL directly. |
| `ehrbase` (2.31.0) | **Clinical / longitudinal store.** openEHR Compositions, AQL queries. | Archetype-versioned storage that survives FHIR profile churn — the reason openEHR exists in this picture at all. |
| `ehrbase-db` (Postgres 16) | EHRbase's relational backing store. | Internal to EHRbase. |
| `fhir-server-2` (HAPI, optional) | Second CH VACD reference server. Default off. | Only used by Pattern B variants that need a destination FHIR system distinct from the source. |

All services share the `vacd-net` Docker network; service-name URLs resolve
inside the network. Published host ports exist for browser/Postman testing
only; runtime calls between services use the internal port via service name.

The PBLL is the only service participants build. Everything else is given
infrastructure.

## Pattern A — Authoritative Platform

**Definition.** The PBLL's FHIR endpoint is the system of record. Clients
write to it directly; citizens and downstream consumers read from it
directly. The platform owns its data.

```
                                  ┌──────────────────────────────────┐
                                  │            Client                 │
                                  │  (vaccinator app, MyVACC, …)      │
                                  └─────────────┬────────────────────┘
                                                │   FHIR (CH VACD)
                                                ▼
                                  ┌──────────────────────────────────┐
                                  │             PBLL                  │
                                  │   Ktor — CH VACD FHIR endpoint    │
                                  └─┬───────────┬───────────────┬────┘
                                    │           │               │
              Patient,              │           │               │  Immunization
              Practitioner,         │           │               │  (clinical)
              Organization,         │           │               │
              metadata helpers      ▼           ▼               ▼
                          ┌─────────────┐  ┌────────────┐  ┌──────────────┐
                          │fhir-server-1│  │  openFHIR  │  │   EHRbase    │
                          │  HAPI 8.8.1 │  │  (mapping) │  │  (Compositions│
                          │  + 17 CH    │  │            │  │   + AQL)      │
                          │  VACD       │  └─────┬──────┘  └──────────────┘
                          │  Resource-  │        │  FLAT JSON       ▲
                          │  Providers  │        └──────────────────┘
                          └─────────────┘
                          Demographics +          Mapping engine    Clinical /
                          directory store         (stateless)        longitudinal
```

**Storage split.** Resources go to whichever store fits their nature:

| Resource | Store | Reason |
|---|---|---|
| Patient | `fhir-server-1` | Identity + demographics. Changes often (name, address). |
| Practitioner | `fhir-server-1` | Directory data — points to people, not clinical events. |
| Organization | `fhir-server-1` | Directory data. |
| Location | `fhir-server-1` | Directory data. |
| PractitionerRole | `fhir-server-1` | Directory data. |
| **Immunization** | **EHRbase** (as `Composition`) | Clinical event. Must survive decades of FHIR profile churn. |

**The link.** Each EHR in EHRbase has an `EHR_STATUS.subject.external_ref`
that points at the Patient in `fhir-server-1`:

```json
"subject": {
  "external_ref": {
    "id":         {"_type": "GENERIC_ID", "value": "<Patient.id>", "scheme": "fhir"},
    "namespace":  "ch-vacd",
    "type":       "PERSON"
  }
}
```

The Composition stores performer/location only as references
(`other_participations` with FHIR identifiers). It does **not** duplicate
demographic content. When a read endpoint wants to return a full CH VACD
Bundle, the PBLL re-hydrates Patient/Practitioner/Organization from
`fhir-server-1` and stitches them back into the Bundle.

**Audit trail.** The Konkretisierung deck mandates that the **original FHIR
Immunization JSON is retained verbatim in the Composition's
`feeder_audit / original_content`**. This is non-negotiable — it's how the
platform provides legal-grade provenance and supports corrections.

**PBLL responsibilities in Pattern A:**

1. **Ingestion.** `POST /Immunization` (or `POST /Bundle` for the full
   document). Fans Patient + Practitioner + Organization to `fhir-server-1`,
   creates or finds an EHR, sends Immunization through openFHIR, stores the
   Composition in EHRbase.
2. **Construction helpers.** `GET /metadata` (own CapabilityStatement),
   `GET /example/Immunization` (a known-valid CH VACD example a client can
   start from). `GET /StructureDefinition/...` is **not** available from
   `fhir-server-1` in the current build, so the PBLL either returns 501 or
   serves bundled snapshots from disk.
3. **Validation (deferred).** HAPI on `fhir-server-1` does **not** support
   `$validate` in this build. Client-side profile validation in the PBLL
   (via the HAPI Java `FhirValidator` with bundled snapshots) is possible
   but heavy; tracer-bullet PRPs skip it and flag it as Iteration 2.
4. **Retrieval** (deferred to a separate PRP).
   `GET /Immunization/{compositionUid}` does a single AQL lookup, converts
   the Composition back to FHIR via openFHIR `/tofhir`, hydrates references
   from `fhir-server-1`, returns a Bundle. Search endpoints
   (`?patient=X&date=...`) require FHIR-search-to-AQL translation that
   openFHIR does **not** provide; the PBLL implements only the subset of
   search params it actually needs.

**When to choose Pattern A:** building a new platform where openEHR is the
data backbone you actually want, and FHIR is the wire format you have to
speak because the world expects it. The MyVACC backend, the central
cantonal vaccination platform, or any greenfield deployment fits here.

## Pattern B — Integration Layer

**Definition.** Same internal architecture as Pattern A, but deployed in a
different boundary: the PBLL is an integrator between *external* systems of
record (clinic EHRs, cantonal registers, the EPR/EPD), not their direct
replacement. The internal stack (`fhir-server-1` + openFHIR + EHRbase)
still does the same things; data primarily flows in/out via connectors.

```
external sources                                              external consumers
(clinic EHRs,                                                (MyVACC, EPR/EPD,
 cantonal registers,                                          other cantons,
 EPR/EPD)                                                     bulk analytics)
        │                                                             ▲
        │  inbound:                                                   │  outbound:
        │   FHIR Subscriptions                                        │   FHIR REST
        │   _lastUpdated polling                                      │   $export bulk
        │   scheduled bulk imports                                    │   FHIR Subscriptions
        ▼                                                             │
   ┌─────────────────────────────────────────────────────────────────┴──────┐
   │                                PBLL                                     │
   │   (Pattern A internals PLUS connectors PLUS sync-state metadata)        │
   └─┬─────────────────────┬─────────────────────────────┬──────────────────┘
     │                     │                             │
     ▼                     ▼                             ▼
   fhir-server-1       openFHIR                       EHRbase
   (local demogs       (mapping)                      (Compositions)
    cache — may be
    skipped if an
    external MPI is
    authoritative)
```

**What Pattern B adds on top of Pattern A:**

- **Inbound connectors.** Each upstream system has its own quirks:
  FHIR Subscriptions over WebSocket or REST-hook; `_lastUpdated` polling
  against systems without Subscriptions; periodic bulk imports for systems
  that only export nightly. Once a resource is inside the PBLL, the
  ingestion path is identical to Pattern A.
- **Outbound connectors.** Consumers either query the PBLL's FHIR endpoint
  (Pattern A read path) or pull bulk extracts via FHIR `$export`. Bulk
  Data Access is its own subprotocol — the PBLL implements it with NDJSON
  output.
- **Sync state metadata.** Watermarks per source, idempotency keys per
  ingested message, conflict markers. Lives in a small Postgres table
  next to the PBLL (NOT in EHRbase or `fhir-server-1`).
- **Conflict resolution hooks.** Policy for "two upstream sources recorded
  the same vaccination differently". The PBLL needs a hook (last-writer-
  wins by default; configurable per deployment).

**What Pattern B does NOT change from Pattern A:**

- The conversion (openFHIR is identical).
- The clinical storage (EHRbase is identical).
- The demographics handling — `fhir-server-1` plays the same role, though
  some Pattern B deployments delegate Patient lookup to an external MPI and
  use HAPI only for Practitioner/Organization caching.

**When to choose Pattern B:** integrating an *existing* FHIR ecosystem by
inserting an openEHR-backed source of truth (or analytics layer) between
the existing systems. Cantonal HIE, central vaccination register pulling
from clinic systems, or any deployment where the PBLL is operationally
"in front of" existing FHIR systems rather than replacing them.

## Comparison

| Criterion | Pattern A | Pattern B |
|---|---|---|
| Where data originates | Direct client POST to PBLL | External FHIR systems |
| Where data is served from | PBLL's FHIR endpoint | PBLL's FHIR endpoint (and/or external consumers) |
| Number of runtime services to demo | 5 (PBLL + fhir-server-1 + openFHIR + Mongo + EHRbase + EHRbase-db) | 5–7 (same + optional `fhir-server-2`, sync-state Postgres) |
| Number of FHIR endpoints clients see | 1 (PBLL) | 1–N (PBLL + external sources/sinks the PBLL doesn't control) |
| Sync / consistency problem | None (single source) | Real — watermarks, idempotency, conflict resolution |
| Implementation effort baseline | Tracer bullet feasible in one PRP | Tracer bullet (Pattern A internals) **plus** at least one connector |
| FHIR-search-to-AQL translation needed | Yes (deferred; trivial subset only) | Less (downstream HAPI does search natively when present) |
| Right for "first example" / GovTech showcase weekend | **Yes** | Reuses Pattern A; better as a follow-up |


## Open architectural questions, parked

These are not Pattern A vs B decisions, but they will surface in any real
deployment:

- **Identity provider.** Citizen authentication (SwissID? SMART on FHIR?)
  is a separate layer in front of the PBLL. Out of scope for the harness;
  flagged here so it's not forgotten.
- **Terminology server.** Konkretisierung (2026-05-12) pins to
  `tx.fhir.ch/r4` (FHIRsmith-based). PBLL value-set lookups and code
  validation would query this; for the tracer bullet, value-sets are
  hardcoded or skipped.
- **Reverse proxy / API gateway.** Konkretisierung shows a gateway
  (Traefik / NGINX / APISIX, decision deferred) between consumers and the
  PBLL. Not in the harness; participants who want one bring their own.
- **Audit and consent.** GDPR consent revocation, audit logging beyond
  `feeder_audit`, are noted as Phase 2 in the Konkretisierung deck. Not in
  the harness.
- **Federation across cantons.** DigiSanté positions this showcase as one
  spoke of a cantonal hub-and-spoke architecture; federation between hubs
  is a downstream concern.
