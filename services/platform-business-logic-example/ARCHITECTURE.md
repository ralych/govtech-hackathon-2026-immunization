# Architecture

How this smoke-test implements the challenge's envisaged architecture.

## Data flow (write path — what's implemented)

```
FHIR Bundle (CH VACD Immunization Administration Document)
  │
  ▼
platform-example :8888
  ├─► Patient, Practitioner, Organization ──► fhir-server-1 (HAPI)
  ├─► Immunization ──► openFHIR /toopenehr ──► EHRbase (Composition)
  └─► original FHIR JSON ──► feeder_audit.original_content (provenance)
```

## Two ways to build the platform layer

**This example: standalone facade (Kotlin/Ktor).** Accepts FHIR, routes
resources to the right stores, orchestrates the openFHIR mapping. You
control everything but reimplement FHIR plumbing (search, validation,
content negotiation). Language-agnostic — rewrite in Python, Go, whatever.

**Alternative: build inside HAPI with interceptors.** The FHIR server
already has the CH VACD ResourceProviders, profile validation, and
search. Add a storage interceptor that routes Immunizations through
openFHIR to EHRbase — the rest (Patient, Practitioner, Organization)
stays in HAPI's own store. Less code, more FHIR compliance out of the
box, but ties you to Java.

Both approaches are valid. Pick whichever gets you to a working demo
fastest.

## What this example skips

- Read path (Composition → openFHIR `/tofhir` → rehydrate references)
- FHIR search (`?patient=X&date=...`)
- Profile validation
- Terminology lookups
