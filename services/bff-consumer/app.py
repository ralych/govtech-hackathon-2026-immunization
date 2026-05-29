import os
from typing import Any
from datetime import date

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

# HACKATHON TESTDATA SEEDING:
# Diese Liste definiert die statischen Standard-Testimpfungen für das Impfdossier von "Anna Schmid".
# Wenn sich ein Patient das erste Mal einloggt und noch keine Impfungen im FHIR-Server existieren,
# werden diese Einträge automatisch in die HAPI-FHIR-Datenbank geschrieben.
MOCK_VACCINATIONS = [
    { "disease": "Diphtherie, Tetanus, Pertussis", "vaccine": "Boostrix", "date": "2005-09-14", "dose": "Booster", "manufacturer": "GlaxoSmithKline", "batch": "BX0509" },
    { "disease": "Diphtherie, Tetanus, Pertussis", "vaccine": "Boostrix", "date": "2015-11-04", "dose": "Booster", "manufacturer": "GlaxoSmithKline", "batch": "DT9923" },
    { "disease": "Masern, Mumps, Röteln", "vaccine": "MMR-VaxPro", "date": "1986-04-18", "dose": "1/2", "manufacturer": "MSD", "batch": "M2204X" },
    { "disease": "Masern, Mumps, Röteln", "vaccine": "MMR-VaxPro", "date": "1992-08-25", "dose": "2/2", "manufacturer": "MSD", "batch": "M5511Y" },
    { "disease": "Hepatitis B", "vaccine": "Engerix-B", "date": "1998-01-12", "dose": "1/3", "manufacturer": "GlaxoSmithKline", "batch": "HB1201", "reason": "Berufliche Exposition" },
    { "disease": "Hepatitis B", "vaccine": "Engerix-B", "date": "1998-02-15", "dose": "2/3", "manufacturer": "GlaxoSmithKline", "batch": "HB1245", "reason": "Berufliche Exposition" },
    { "disease": "Hepatitis B", "vaccine": "Engerix-B", "date": "1998-08-10", "dose": "3/3", "manufacturer": "GlaxoSmithKline", "batch": "HB1502", "reason": "Berufliche Exposition" },
    { "disease": "FSME", "vaccine": "FSME-Immun CC", "date": "2012-04-02", "dose": "1/3", "manufacturer": "Pfizer", "batch": "FS3301" },
    { "disease": "FSME", "vaccine": "FSME-Immun CC", "date": "2012-05-30", "dose": "2/3", "manufacturer": "Pfizer", "batch": "FS3344" },
    { "disease": "FSME", "vaccine": "FSME-Immun CC", "date": "2013-03-12", "dose": "3/3", "manufacturer": "Pfizer", "batch": "FS4012" },
    { "disease": "FSME", "vaccine": "FSME-Immun CC", "date": "2023-04-19", "dose": "Booster", "manufacturer": "Pfizer", "batch": "FS9912", "reason": "Endemiegebiet / Freizeit" },
    { "disease": "COVID-19", "vaccine": "Comirnaty (BNT162b2)", "date": "2021-05-21", "dose": "1/2", "manufacturer": "Pfizer-BioNTech", "batch": "EW0150" },
    { "disease": "COVID-19", "vaccine": "Comirnaty (BNT162b2)", "date": "2021-06-18", "dose": "2/2", "manufacturer": "Pfizer-BioNTech", "batch": "FA2421" },
    { "disease": "COVID-19", "vaccine": "Comirnaty Omicron XBB.1.5", "date": "2023-10-12", "dose": "Booster", "manufacturer": "Pfizer-BioNTech", "batch": "GH8821", "reason": "Empfehlung BAG" },
    { "disease": "Influenza (saisonal)", "vaccine": "Fluarix Tetra", "date": "2024-10-08", "dose": "Saison 24/25", "manufacturer": "GlaxoSmithKline", "batch": "FLU2425", "reason": "Empfehlung BAG (Chronische Erkrankung)" },
    { "disease": "Influenza (saisonal)", "vaccine": "Fluarix Tetra", "date": "2025-10-14", "dose": "Saison 25/26", "manufacturer": "GlaxoSmithKline", "batch": "FLU2526", "reason": "Empfehlung BAG (Chronische Erkrankung)" }
]


@app.get("/healthz")
async def healthz() -> dict[str, Any]:
    try:
        r = await fhir.get(f"{FHIR_BASE}/metadata")
        return {"ok": r.status_code == 200, "fhirStatus": r.status_code}
    except Exception as e:
        return {"ok": False, "error": str(e)}


@app.get("/patients/{patient_id}")
async def get_patient_dossier(patient_id: str) -> dict[str, Any]:
    # Ensure the patient resource exists
    await _ensure_patient(patient_id)

    # Fetch patient details
    r_pat = await fhir.get(f"{FHIR_BASE}/Patient/{patient_id}")
    if r_pat.status_code != 200:
        raise HTTPException(
            status_code=502,
            detail=f"FHIR server returned {r_pat.status_code} for Patient GET: {r_pat.text[:200]}",
        )
    pat = r_pat.json()

    # Extract names (mit Fallback auf unsere Standard-Testperson "Anna Schmid")
    names = pat.get("name") or []
    first_name = "Anna"
    last_name = "Schmid"
    if names:
        given = names[0].get("given") or []
        if given:
            first_name = given[0]
        last_name = names[0].get("family") or "Schmid"

    gender_raw = pat.get("gender") or "female"
    gender_map = {"female": "weiblich", "male": "männlich", "other": "divers", "unknown": "divers"}
    gender = gender_map.get(gender_raw, "weiblich")

    # If this is a newly created patient resource, it won't have a birth date. We can use the default.
    birth_date = pat.get("birthDate") or "1985-03-12"

    try:
        dob = date.fromisoformat(birth_date)
        today = date.today()
        age = today.year - dob.year - ((today.month, today.day) < (dob.month, dob.day))
    except Exception:
        age = 41

    # Seed vaccinations if the database is currently empty for this patient
    await _seed_vaccinations_if_empty(patient_id)

    # Fetch vaccinations from FHIR
    r_imm = await fhir.get(
        f"{FHIR_BASE}/Immunization",
        params={"patient": patient_id, "_count": "200"},
    )
    if r_imm.status_code != 200:
        raise HTTPException(
            status_code=502,
            detail=f"FHIR server returned {r_imm.status_code} for Immunization GET: {r_imm.text[:200]}",
        )
    bundle = r_imm.json()
    entries = bundle.get("entry") or []

    vaccinations = []
    for e in entries:
        imm = e["resource"]
        vaccinations.append(_to_dto(imm))

    return {
        "firstName": first_name,
        "lastName": last_name,
        "age": age,
        "gender": gender,
        "vaccinations": vaccinations
    }


# Backwards compatibility endpoint
@app.get("/patients/{patient_id}/vaccinations")
async def list_vaccinations(patient_id: str) -> dict[str, Any]:
    dossier = await get_patient_dossier(patient_id)
    return {
        "patientId": patient_id,
        "patientCreated": False,
        "count": len(dossier["vaccinations"]),
        "vaccinations": dossier["vaccinations"]
    }


async def _ensure_patient(patient_id: str) -> bool:
    """Return True if the patient had to be created, False if it already existed."""
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
        
    # Create patient with realistic metadata (Fallback auf "Anna Schmid")
    pat_payload = {
        "resourceType": "Patient",
        "id": patient_id,
        "name": [
            {
                "family": "Schmid",
                "given": ["Anna"]
            }
        ],
        "gender": "female",
        "birthDate": "1985-03-12"
    }
    
    create = await fhir.put(
        f"{FHIR_BASE}/Patient/{patient_id}",
        json=pat_payload,
        headers={"Content-Type": "application/fhir+json"},
    )
    if create.status_code not in (200, 201):
        raise HTTPException(
            status_code=502,
            detail=f"Patient create failed ({create.status_code}): {create.text[:200]}",
        )
    return True


async def _seed_vaccinations_if_empty(patient_id: str):
    # Check if this patient already has immunizations
    r = await fhir.get(
        f"{FHIR_BASE}/Immunization",
        params={"patient": patient_id, "_count": "1"},
    )
    if r.status_code == 200:
        bundle = r.json()
        if (bundle.get("total") or 0) > 0 or (bundle.get("entry") or []):
            return  # Already has data, do not seed

    DISEASE_CODINGS = {
        "Diphtherie": {"system": "http://snomed.info/sct", "code": "397430003", "display": "Diphtheria caused by Corynebacterium diphtheriae (disorder)"},
        "Tetanus": {"system": "http://snomed.info/sct", "code": "76902006", "display": "Tetanus (disorder)"},
        "Pertussis": {"system": "http://snomed.info/sct", "code": "27836007", "display": "Pertussis (disorder)"},
        "Polio": {"system": "http://snomed.info/sct", "code": "398102009", "display": "Acute poliomyelitis (disorder)"},
        "Masern": {"system": "http://snomed.info/sct", "code": "14189004", "display": "Measles (disorder)"},
        "Mumps": {"system": "http://snomed.info/sct", "code": "36989005", "display": "Mumps (disorder)"},
        "Röteln": {"system": "http://snomed.info/sct", "code": "36653000", "display": "Rubella (disorder)"},
        "Hepatitis B": {"system": "http://snomed.info/sct", "code": "66071002", "display": "Viral hepatitis type B (disorder)"},
        "FSME": {"system": "http://snomed.info/sct", "code": "712986001", "display": "Central European encephalitis (disorder)"},
        "COVID-19": {"system": "http://snomed.info/sct", "code": "840539006", "display": "COVID-19 (disorder)"},
        "Influenza (saisonal)": {"system": "http://snomed.info/sct", "code": "6142004", "display": "Influenza (disorder)"},
        "Pneumokokken": {"system": "http://snomed.info/sct", "code": "16814004", "display": "Pneumococcal infectious disease (disorder)"}
    }

    for mv in MOCK_VACCINATIONS:
        diseases = [d.strip() for d in mv["disease"].split(",")]
        target_coding = []
        for d in diseases:
            if d in DISEASE_CODINGS:
                target_coding.append(DISEASE_CODINGS[d])
            else:
                target_coding.append({"system": "http://snomed.info/sct", "code": "11111111", "display": f"{d} (disorder)"})
                
        imm_resource = {
            "resourceType": "Immunization",
            "status": "completed",
            "vaccineCode": {
                "coding": [
                    {
                        "system": "http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs",
                        "code": "100",
                        "display": mv["vaccine"]
                    }
                ]
            },
            "patient": {
                "reference": f"Patient/{patient_id}"
            },
            "occurrenceDateTime": mv["date"],
            "lotNumber": mv["batch"],
            "protocolApplied": [
                {
                    "targetDisease": [
                        {
                            "coding": [tc]
                        } for tc in target_coding
                    ],
                    "doseNumberString": mv["dose"]
                }
            ]
        }
        
        if "reason" in mv:
            # Map seed reason text to correct SNOMED CT / Swiss Risk ValueSet code
            reason_seed_map = {
                "Berufliche Exposition": ("223366009", "Healthcare professional"),
                "Endemiegebiet / Freizeit": ("1237021005", "At increased risk of exposure to European tick-borne encephalitis virus"),
                "Empfehlung BAG": ("870577009", "At increased risk of exposure to SARS-CoV-2"),
                "Empfehlung BAG (Chronische Erkrankung)": ("1237028004", "At increased risk of exposure to Influenza virus"),
            }
            code_str, display_str = reason_seed_map.get(mv["reason"], ("373068000", mv["reason"]))
            
            imm_resource["reasonCode"] = [
                {
                    "coding": [
                        {
                            "system": "http://snomed.info/sct",
                            "code": code_str,
                            "display": display_str
                        }
                    ]
                }
            ]

        await fhir.post(
            f"{FHIR_BASE}/Immunization",
            json=imm_resource,
            headers={"Content-Type": "application/fhir+json"}
        )


def _to_dto(imm: dict[str, Any]) -> dict[str, Any]:
    coding = ((imm.get("vaccineCode") or {}).get("coding") or [{}])[0]
    vaccine_name = coding.get("display") or coding.get("code") or "Unbekannter Impfstoff"
    
    proto = (imm.get("protocolApplied") or [{}])[0]
    dose = proto.get("doseNumberPositiveInt") or proto.get("doseNumberString")
    dose_str = str(dose) if dose is not None else None
    
    target_diseases = []
    for td in (proto.get("targetDisease") or []):
        td_coding = ((td.get("coding") or [{}])[0])
        display = td_coding.get("display") or td_coding.get("code")
        if display:
            target_diseases.append(display)
            
    if target_diseases:
        translated = []
        for d in target_diseases:
            d_lower = d.lower()
            if "tetanus" in d_lower:
                translated.append("Tetanus")
            elif "pertussis" in d_lower:
                translated.append("Pertussis")
            elif "diphtheria" in d_lower:
                translated.append("Diphtherie")
            elif "poliomyelitis" in d_lower:
                translated.append("Polio")
            elif "measles" in d_lower or "masern" in d_lower:
                translated.append("Masern")
            elif "mumps" in d_lower:
                translated.append("Mumps")
            elif "rubella" in d_lower or "röteln" in d_lower:
                translated.append("Röteln")
            elif "hepatitis b" in d_lower:
                translated.append("Hepatitis B")
            elif "encephalitis" in d_lower or "fsme" in d_lower:
                translated.append("FSME")
            elif "influenza" in d_lower:
                translated.append("Influenza (saisonal)")
            elif "pneumococcal" in d_lower or "pneumo" in d_lower:
                translated.append("Pneumokokken")
            else:
                translated.append(d)
        target_disease = ", ".join(sorted(list(set(translated))))
    else:
        target_disease = infer_disease(vaccine_name)
        
    if "Diphtherie" in target_disease and "Tetanus" in target_disease and "Pertussis" in target_disease:
        if "Polio" in target_disease:
            if "Hepatitis B" in target_disease:
                target_disease = "Diphtherie, Tetanus, Pertussis, Polio, Hib, Hepatitis B"
            else:
                target_disease = "Diphtherie, Tetanus, Pertussis, Polio"
        else:
            target_disease = "Diphtherie, Tetanus, Pertussis"
    elif "Masern" in target_disease and "Mumps" in target_disease and "Röteln" in target_disease:
        target_disease = "Masern, Mumps, Röteln"

    manufacturer = "Unbekannter Hersteller"
    v_lower = vaccine_name.lower()
    if "boostrix" in v_lower or "infanrix" in v_lower or "engerix" in v_lower or "priorix" in v_lower or "twinrix" in v_lower or "havrix" in v_lower or "fluarix" in v_lower:
        manufacturer = "GlaxoSmithKline"
    elif "comirnaty" in v_lower or "fsme-immun" in v_lower or "prevenar" in v_lower:
        manufacturer = "Pfizer"
    elif "spikevax" in v_lower:
        manufacturer = "Moderna"
    elif "mmr" in v_lower:
        manufacturer = "MSD"
        
    route_raw = ((imm.get("route") or {}).get("coding") or [{}])[0].get("display")
    site_raw = ((imm.get("site") or {}).get("coding") or [{}])[0].get("display")
    
    season = None
    date_str = imm.get("occurrenceDateTime") or ""
    if "influenza" in target_disease.lower() and date_str:
        try:
            year = int(date_str.split("-")[0])
            month = int(date_str.split("-")[1])
            if month >= 8:
                season = f"Saison {str(year)[2:]}/{str(year+1)[2:]}"
            else:
                season = f"Saison {str(year-1)[2:]}/{str(year)[2:]}"
        except Exception:
            season = "Saison 25/26"

    dose_number_val = None
    series_doses_val = None
    if dose_str:
        if "/" in dose_str:
            parts = dose_str.split("/", 1)
            dose_number_val = parts[0]
            series_doses_val = parts[1]
        elif dose_str.lower() == "booster":
            dose_number_val = "Booster"
        else:
            dose_number_val = dose_str

    SWISS_REASON_MAP = {
        "373068000": {"code": "373068000", "display": "Not known", "swissLabel": "Grundimmunisierung"},
        "Booster": {"code": "Booster", "display": "Booster", "swissLabel": "Auffrischimpfung (Booster)"},
        "Nachholimpfung": {"code": "Nachholimpfung", "display": "Nachholimpfung", "swissLabel": "Nachholimpfung"},
        "14747002": {"code": "14747002", "display": "Elective immunization for international travel", "swissLabel": "Reiseimpfung"},
        "223366009": {"code": "223366009", "display": "Healthcare professional", "swissLabel": "Risikogruppe — beruflich"},
        "56265001": {"code": "56265001", "display": "Heart disease", "swissLabel": "Risikogruppe — medizinisch"},
        "Postexpositionsprophylaxe": {"code": "Postexpositionsprophylaxe", "display": "Postexpositionsprophylaxe", "swissLabel": "Postexpositionsprophylaxe"},
        "1237028004": {"code": "1237028004", "display": "At increased risk of exposure to Influenza virus", "swissLabel": "Saisonale Impfung"},
        "1237021005": {"code": "1237021005", "display": "At increased risk of exposure to European tick-borne encephalitis virus", "swissLabel": "Risikogruppe — Freizeit (FSME)"},
        "870577009": {"code": "870577009", "display": "At increased risk of exposure to SARS-CoV-2", "swissLabel": "Empfehlung BAG"},
    }

    reason_code_list = imm.get("reasonCode") or []
    reason_obj = None
    if reason_code_list:
        coding = (reason_code_list[0].get("coding") or [{}])[0]
        code_val = coding.get("code")
        display_val = coding.get("display") or coding.get("code")
        
        if code_val in SWISS_REASON_MAP:
            reason_obj = SWISS_REASON_MAP[code_val]
        else:
            found_map = None
            for key, val in SWISS_REASON_MAP.items():
                if val["swissLabel"].lower() == display_val.lower() or val["display"].lower() == display_val.lower():
                    found_map = val
                    break
            
            if found_map:
                reason_obj = found_map
            else:
                reason_obj = {
                    "code": code_val or "373068000",
                    "display": display_val,
                    "swissLabel": display_val
                }

    return {
        "targetDisease": target_disease,
        "vaccineName": vaccine_name,
        "season": season,
        "doseSequence": dose_str or "1/1",
        "doseNumber": dose_number_val,
        "seriesDoses": series_doses_val,
        "vaccinationReason": reason_obj,
        "vaccinationDate": date_str[:10] if date_str else "2026-05-29",
        "manufacturer": manufacturer,
        "lotNumber": imm.get("lotNumber") or "UNKNOWN",
        "administrationRoute": route_raw or "i.m.",
        "siteOfAdministration": site_raw or "Oberarm links"
    }


def infer_disease(vaccine: str) -> str:
    v = vaccine.lower()
    if "boostrix polio" in v or "infanrix-ipv" in v or "pentavac" in v:
        return "Diphtherie, Tetanus, Pertussis, Polio"
    if "infanrix hexa" in v:
        return "Diphtherie, Tetanus, Pertussis, Polio, Hib, Hepatitis B"
    if "boostrix" in v:
        return "Diphtherie, Tetanus, Pertussis"
    if "td-pur" in v:
        return "Diphtherie, Tetanus"
    if "priorix" in v or "mmr" in v:
        return "Masern, Mumps, Röteln"
    if "gardasil" in v:
        return "HPV"
    if "fsme" in v or "encepur" in v:
        return "FSME"
    if "engerix" in v:
        return "Hepatitis B"
    if "havrix" in v:
        return "Hepatitis A"
    if "twinrix" in v:
        return "Hepatitis A + B"
    if "comirnaty" in v or "spikevax" in v or "covid" in v:
        return "COVID-19"
    if "influvac" in v or "fluarix" in v or "efluelda" in v:
        return "Influenza (saisonal)"
    if "prevenar" in v or "pneumo" in v:
        return "Pneumokokken"
    if "menveo" in v or "nimenrix" in v:
        return "Meningokokken ACWY"
    if "rabipur" in v:
        return "Tollwut"
    if "stamaril" in v:
        return "Gelbfieber"
    if "shingrix" in v:
        return "Herpes Zoster"
    if "varilrix" in v:
        return "Varizellen"
    return "Sonstige"

