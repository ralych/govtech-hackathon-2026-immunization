# CH VACD Example Bundles

Canonical FHIR R4 examples for testing the harness end-to-end.

Every file here is a **CH VACD Immunization Administration Document** —
a FHIR `Bundle` of type `document` whose first entry is a `Composition`
referencing a `Patient`, `Practitioner`, `Organization`,
`PractitionerRole`, `Immunization`, and a `Binary` payload (per the
[CH VACD IG](https://fhir.ch/ig/ch-vacd/)).

These are **harness assets**, not internal to the example platform. Any
platform implementation (the example under
`services/platform-business-logic-example/`, or one a team writes) should
test against the Bundles here.

## What's in here

| File | Vaccine | Patient | Recorded | Source |
|---|---|---|---|---|
| `01-immunization-administration-boostrix.json` | Boostrix (Swissmedic 637) — dT-IPV booster | Monika Wegmueller, F, 1967 | 2017-09-15 | **Verbatim copy** of [`Bundle-1-1-ImmunizationAdministration.json`](https://fhir.ch/ig/ch-vacd/Bundle-1-1-ImmunizationAdministration.json) from the CH VACD IG |
| `02-immunization-administration-comirnaty.json` | Comirnaty (Swissmedic 68225) — COVID-19 mRNA | Andreas Schmid, M, 1982 | 2024-10-21 | Derived from #01 by `build_examples.py` |
| `03-immunization-administration-priorix.json` | Priorix (Swissmedic 615) — MMR | Lea Müller, F, 2019 | 2023-08-14 | Derived from #01 by `build_examples.py` |

The seed (#01) is the gold standard from the IG and must not drift; if
the upstream Bundle changes, re-fetch it and re-run `build_examples.py`
to regenerate the derived files.

## How codes were verified

The implementing session that built PRP-01 invented plausible-looking
Swissmedic codes. We're not doing that again.

| Code | Verified against | Version |
|---|---|---|
| Swissmedic vaccine codes (`vaccineCode.coding`) | [`CodeSystem-ch-vacd-swissmedic-cs.json`](https://fhir.ch/ig/ch-vacd/CodeSystem-ch-vacd-swissmedic-cs.json) | 4.0.1 |
| SNOMED CT target-disease codes (`protocolApplied[].targetDisease[].coding`) | [`ValueSet-ch-vacd-targetdiseasesandillnessesundergoneforimmunization-vs.json`](https://fhir.ch/ig/ch-vacd/ValueSet-ch-vacd-targetdiseasesandillnessesundergoneforimmunization-vs.json) | — |
| EDQM Standard Terms route codes (`route.coding`) | inherited verbatim from the seed Bundle (IG-published example) | — |
| Composition / section LOINC codes | inherited verbatim from the seed | — |

Every code in the examples is traceable back to one of these
authorities. If a future variant needs a code that isn't in the lists
above, fetch the source and verify before adding.

## Validating an example

The reference server doesn't run FHIR profile validation — it stores
JSON blobs. So our basic "is this well-formed?" check is a round-trip:
POST → GET → assert the resourceType and structure survive.

```sh
./roundtrip.sh                        # all three examples
./roundtrip.sh 02-*.json              # a specific one
FHIR_BASE=http://… ./roundtrip.sh     # override target server
```

What the script does per Bundle:
1. POSTs the whole document to `/Bundle`. HAPI parses it as FHIR R4;
   if the JSON is malformed or any cross-reference points at a resource
   type HAPI doesn't recognise, this fails.
2. For each entry whose `resourceType` has a provider on the ref server
   (Patient, Practitioner, Organization, PractitionerRole, Immunization
   — Composition and Binary are intentionally not exposed as individual
   endpoints in `application.yml`'s `fhir.providers` list), POSTs the
   resource and GETs it back, asserting the response has the same
   `resourceType`.

This is the lightest possible check and **does not** prove CH VACD
profile conformance. Real profile validation needs HAPI's
`FhirValidator` loaded with the CH VACD snapshot package — out of scope
for the harness; see the IG's [validation tools](https://fhir.ch/ig/ch-vacd/).

## Adding a new example

1. Pick a vaccine that exists in `ch-vacd-swissmedic-cs`.
2. Pick a target disease that exists in the
   `ch-vacd-targetdiseasesandillnessesundergoneforimmunization-vs`
   ValueSet.
3. Add a new variant block to `build_examples.py` and re-run it.
4. Run `./roundtrip.sh` and confirm everything's green.
5. Add a row to the table above.

