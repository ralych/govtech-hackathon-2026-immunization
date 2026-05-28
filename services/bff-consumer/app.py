import os
from typing import Any

import httpx
from fastapi import FastAPI, HTTPException

FHIR_BASE = os.getenv(
    "FHIR_BASE_URL",
    "http://fhir-server-1:9111/ch-vacd-api-reference-server/fhir",
)
TIMEOUT = float(os.getenv("FHIR_TIMEOUT_SECONDS", "10"))

app = FastAPI(title="CH VACD Consumer BFF", version="0.1.0")
fhir = httpx.AsyncClient(
    timeout=TIMEOUT,
    headers={"Accept": "application/fhir+json"},
)


@app.get("/healthz")
async def healthz() -> dict[str, Any]:
    try:
        r = await fhir.get(f"{FHIR_BASE}/metadata")
        return {"ok": r.status_code == 200, "fhirStatus": r.status_code}
    except Exception as e:
        return {"ok": False, "error": str(e)}


@app.get("/patients/{patient_id}/vaccinations")
async def list_vaccinations(patient_id: str) -> dict[str, Any]:
    patient_created = await _ensure_patient(patient_id)

    r = await fhir.get(
        f"{FHIR_BASE}/Immunization",
        params={"patient": patient_id, "_count": "200"},
    )
    if r.status_code != 200:
        raise HTTPException(
            status_code=502,
            detail=f"FHIR server returned {r.status_code}: {r.text[:200]}",
        )
    bundle = r.json()
    entries = bundle.get("entry") or []
    return {
        "patientId": patient_id,
        "patientCreated": patient_created,
        "count": len(entries),
        "vaccinations": [_to_view(e["resource"]) for e in entries],
    }


async def _ensure_patient(patient_id: str) -> bool:
    """Return True if the patient had to be created, False if it already existed."""
    # The CH VACD reference PatientProvider returns a synthetic stub for any
    # GET /Patient/{id} (never 404), so we check via Search which only yields
    # actually-stored Patients.
    r = await fhir.get(f"{FHIR_BASE}/Patient", params={"_count": "1000"})
    if r.status_code != 200:
        raise HTTPException(
            status_code=502,
            detail=f"Patient search failed ({r.status_code}): {r.text[:200]}",
        )
    stored_ids = {
        (e.get("resource") or {}).get("id")
        for e in (r.json().get("entry") or [])
    }
    if patient_id in stored_ids:
        return False
    create = await fhir.put(
        f"{FHIR_BASE}/Patient/{patient_id}",
        json={"resourceType": "Patient", "id": patient_id},
        headers={"Content-Type": "application/fhir+json"},
    )
    if create.status_code not in (200, 201):
        raise HTTPException(
            status_code=502,
            detail=f"Patient create failed ({create.status_code}): {create.text[:200]}",
        )
    return True


def _to_view(imm: dict[str, Any]) -> dict[str, Any]:
    coding = ((imm.get("vaccineCode") or {}).get("coding") or [{}])[0]
    proto = (imm.get("protocolApplied") or [{}])[0]
    target = [
        ((td.get("coding") or [{}])[0]).get("display")
        for td in (proto.get("targetDisease") or [])
    ]
    performer = [
        p.get("actor", {}).get("reference") for p in (imm.get("performer") or [])
    ]
    return {
        "id": imm.get("id"),
        "status": imm.get("status"),
        "date": imm.get("occurrenceDateTime"),
        "vaccine": {
            "code": coding.get("code"),
            "system": coding.get("system"),
            "display": coding.get("display"),
        },
        "lotNumber": imm.get("lotNumber"),
        "doseNumber": proto.get("doseNumberPositiveInt")
        or proto.get("doseNumberString"),
        "targetDiseases": [d for d in target if d],
        "performerRefs": [p for p in performer if p],
    }
