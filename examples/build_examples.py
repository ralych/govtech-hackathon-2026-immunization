#!/usr/bin/env python3
"""
Build CH VACD example Bundles from the official seed.

Seed: examples/01-immunization-administration-boostrix.json
  (which is a verbatim copy of
   https://fhir.ch/ig/ch-vacd/Bundle-1-1-ImmunizationAdministration.json — the
   gold-standard CH VACD Immunization Administration Document.)

This script derives 2 additional variants:
  - 02-immunization-administration-comirnaty.json  (COVID-19 Comirnaty)
  - 03-immunization-administration-priorix.json    (MMR Priorix)

All vaccine codes verified against
  https://fhir.ch/ig/ch-vacd/CodeSystem-ch-vacd-swissmedic-cs.json  (v4.0.1)

All target-disease codes verified against
  https://fhir.ch/ig/ch-vacd/ValueSet-ch-vacd-targetdiseasesandillnessesundergoneforimmunization-vs.json

Run with:  python3 build_examples.py
"""

import copy
import json
from pathlib import Path

HERE = Path(__file__).parent
SEED = HERE / "01-immunization-administration-boostrix.json"


def load_seed():
    return json.loads(SEED.read_text())


def re_id(bundle, suffix, patient_name, patient_birthDate, patient_gender, identifier_value):
    """
    Mutates the Bundle in place: rewrite all entry ids/fullUrls with the suffix,
    swap Patient demographics, leave structure intact.

    The seed Bundle uses fullUrls like http://test.fhir.ch/r4/<Type>/<id>; we
    keep the same base and only swap the trailing id segment so all
    cross-references stay internally consistent.
    """
    bundle["id"] = bundle["id"].replace("1-1", f"1-{suffix}", 1)
    base = "http://test.fhir.ch/r4"

    # Map old fullUrl -> new fullUrl
    rewrites = {}
    for entry in bundle["entry"]:
        old_full = entry["fullUrl"]
        # old full looks like http://test.fhir.ch/r4/Patient/3-1-Patient
        rtype = entry["resource"]["resourceType"]
        old_id = entry["resource"]["id"]
        new_id = old_id.replace("-1-", f"-{suffix}-", 1)
        if old_id == new_id:  # fallback (no "-1-" present)
            new_id = f"{old_id}-{suffix}"
        entry["resource"]["id"] = new_id
        new_full = f"{base}/{rtype}/{new_id}"
        entry["fullUrl"] = new_full
        rewrites[old_full] = new_full
        # Also map the short ref form Patient/3-1-Patient -> Patient/3-N-Patient
        rewrites[f"{rtype}/{old_id}"] = f"{rtype}/{new_id}"

    # Walk the tree and rewrite any "reference" strings.
    def walk(node):
        if isinstance(node, dict):
            if "reference" in node and isinstance(node["reference"], str):
                if node["reference"] in rewrites:
                    node["reference"] = rewrites[node["reference"]]
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for v in node:
                walk(v)

    walk(bundle)

    # Patch the Patient resource demographics.
    patient = next(e["resource"] for e in bundle["entry"]
                   if e["resource"]["resourceType"] == "Patient")
    family, given = patient_name
    patient["name"] = [{"family": family, "given": [given]}]
    patient["birthDate"] = patient_birthDate
    patient["gender"] = patient_gender
    if patient.get("identifier"):
        patient["identifier"][0]["value"] = identifier_value
    # Strip the bulky narrative text — it's generated and would lie about the new data.
    patient.pop("text", None)


def patch_immunization(bundle, *, vaccine_coding, occurrence, lot, target_diseases, suffix):
    """Mutates the Immunization in the Bundle: swap vaccineCode, lotNumber,
    occurrence/recorded, targetDisease. Strip narrative and identifier value
    suffix so each example is independent."""
    imm = next(e["resource"] for e in bundle["entry"]
               if e["resource"]["resourceType"] == "Immunization")
    imm["vaccineCode"] = {"coding": [vaccine_coding]}
    imm["occurrenceDateTime"] = occurrence
    imm["recorded"] = occurrence
    imm["lotNumber"] = lot
    if imm.get("identifier"):
        imm["identifier"][0]["value"] = f"{imm['identifier'][0]['value']}-{suffix}"
    # Replace protocolApplied[0].targetDisease wholesale.
    imm["protocolApplied"][0]["targetDisease"] = [
        {"coding": [c]} for c in target_diseases
    ]
    imm.pop("text", None)


def patch_composition(bundle, *, title, date):
    comp = next(e["resource"] for e in bundle["entry"]
                if e["resource"]["resourceType"] == "Composition")
    comp["title"] = title
    comp["date"] = date
    comp.pop("text", None)


def patch_bundle_timestamp(bundle, ts):
    bundle["timestamp"] = ts
    if bundle.get("identifier"):
        # keep the OID, swap the value so the Bundle has its own identity.
        bundle["identifier"]["value"] = f"{bundle['identifier']['value']}-derived"


SNOMED = "http://snomed.info/sct"
SWISSMEDIC = "http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs"


# Variant 1: COVID-19 Comirnaty
b = load_seed()
re_id(b,
      suffix="2",
      patient_name=("Schmid", "Andreas"),
      patient_birthDate="1982-11-04",
      patient_gender="male",
      identifier_value="87654321")
patch_immunization(b,
    vaccine_coding={"system": SWISSMEDIC, "code": "68225",
                    "display": "Comirnaty (COVID-19 Vaccine, Pfizer)"},
    occurrence="2024-10-21T10:30:00+02:00",
    lot="HC9876",
    target_diseases=[
        {"system": SNOMED, "code": "840539006",
         "display": "Disease caused by Severe acute respiratory syndrome coronavirus 2 (disorder)"},
    ],
    suffix="2")
patch_composition(b, title="Immunization Administration — COVID-19 Comirnaty",
                  date="2024-10-21T10:30:00+02:00")
patch_bundle_timestamp(b, "2024-10-21T10:30:00+02:00")
(HERE / "02-immunization-administration-comirnaty.json").write_text(
    json.dumps(b, indent=2) + "\n")


# Variant 2: MMR Priorix
b = load_seed()
re_id(b,
      suffix="3",
      patient_name=("Müller", "Lea"),
      patient_birthDate="2019-06-22",
      patient_gender="female",
      identifier_value="55501234")
patch_immunization(b,
    vaccine_coding={"system": SWISSMEDIC, "code": "615",
                    "display": "Priorix"},
    occurrence="2023-08-14T14:15:00+02:00",
    lot="MMR-2023-Q3",
    target_diseases=[
        {"system": SNOMED, "code": "14189004", "display": "Measles (disorder)"},
        {"system": SNOMED, "code": "36989005", "display": "Mumps (disorder)"},
        {"system": SNOMED, "code": "36653000", "display": "Rubella (disorder)"},
    ],
    suffix="3")
patch_composition(b, title="Immunization Administration — MMR Priorix",
                  date="2023-08-14T14:15:00+02:00")
patch_bundle_timestamp(b, "2023-08-14T14:15:00+02:00")
(HERE / "03-immunization-administration-priorix.json").write_text(
    json.dumps(b, indent=2) + "\n")


print("Built:")
for f in sorted(HERE.glob("*.json")):
    print(f"  {f.name:60s} ({f.stat().st_size:>6d} bytes)")
