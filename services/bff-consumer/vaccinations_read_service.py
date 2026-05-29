"""Python port of bff-producer's VacctinationsReadService.

Reads a CH VACD VaccinationRecord Bundle (containing a Composition with an
immunization-history section keyed by LOINC 11369-6) from the FHIR server and
maps the contained Immunization resources to VaccinationDto records.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import date, datetime
from typing import Any
from uuid import UUID

import httpx

IMMUNIZATION_LOINC_CODE = "11369-6"
LOINC_SYSTEM = "http://loinc.org"
_GLN_PATTERN = re.compile(r"^7601\d{9}$")


@dataclass(frozen=True)
class PractitionerDto:
    doctor_name: str
    gln: str | None

    def __post_init__(self) -> None:
        if not self.doctor_name or not self.doctor_name.strip():
            raise ValueError("Arztname darf nicht leer sein")
        if self.gln is not None and not _GLN_PATTERN.match(self.gln):
            raise ValueError(
                "Ungültiges GLN-Format (muss eine 13-stellige Zahl beginnend mit 7601 sein)"
            )


@dataclass(frozen=True)
class VaccinationDto:
    id: UUID
    vaccine_name: str
    vaccine_code: str
    dose_sequence: str
    vaccination_date: date
    manufacturer: str
    lot_number: str
    administration_route: str | None
    site_of_administration: str | None
    practitioner: PractitionerDto
    reason: str | None

    def __post_init__(self) -> None:
        if self.id is None:
            raise ValueError("ID darf nicht null sein")
        if not self.vaccine_name or not self.vaccine_name.strip():
            raise ValueError("Impfstoffname darf nicht leer sein")
        if not self.vaccine_code or not self.vaccine_code.strip():
            raise ValueError("Impfstoffcode darf nicht leer sein")
        if not self.dose_sequence or not self.dose_sequence.strip():
            raise ValueError("Dosis-Reihenfolge (z.B. 1/2) darf nicht leer sein")
        if self.vaccination_date is None:
            raise ValueError("Impfdatum darf nicht null sein")
        if not self.manufacturer or not self.manufacturer.strip():
            raise ValueError("Hersteller darf nicht leer sein")
        if not self.lot_number or not self.lot_number.strip():
            raise ValueError("Chargennummer (Lot) darf nicht leer sein")
        if self.practitioner is None:
            raise ValueError("Angaben zur medizinischen Fachperson dürfen nicht null sein")


class FhirClient:
    """Async equivalent of the Feign FhirClient used by the producer."""

    def __init__(self, client: httpx.AsyncClient, base_url: str) -> None:
        self._client = client
        self._base_url = base_url.rstrip("/")

    async def get_vaccination_record(self, patient_iam_id: str) -> dict[str, Any]:
        response = await self._client.get(
            f"{self._base_url}/Immunization",
            params={"patient": patient_iam_id},
        )
        response.raise_for_status()
        return response.json()


class VaccinationsReadService:
    def __init__(self, fhir_client: FhirClient) -> None:
        self._fhir_client = fhir_client

    async def get_vaccination_list(self, patient_iam_id: str) -> list[VaccinationDto]:
        bundle = await self._fhir_client.get_vaccination_record(patient_iam_id)
        print(f"### {bundle}")
        return [
            _to_vaccination_dto(immunization)
            for entry in bundle.get("entry") or []
            if (composition := entry.get("resource"))
            and composition.get("resourceType") == "Composition"
            for section in composition.get("section") or []
            if _section_has_immunization_loinc(section)
            for section_entry in section.get("entry") or []
            if (immunization := _resolve_immunization(section_entry, bundle))
        ]


def _section_has_immunization_loinc(section: dict[str, Any]) -> bool:
    return any(
        coding.get("system") == LOINC_SYSTEM
        and coding.get("code") == IMMUNIZATION_LOINC_CODE
        for coding in (section.get("code") or {}).get("coding") or []
    )


def _resolve_immunization(
    section_entry: dict[str, Any],
    bundle: dict[str, Any]
) -> dict[str, Any] | None:
    """Section entries in a CH VACD Bundle are references like 'Immunization/123'.

    Resolve them against the same bundle's entries (matched on fullUrl or id).
    """
    reference = section_entry.get("reference") or ""
    if not reference:
        return None
    target_id = reference.split("/", 1)[-1]
    for entry in bundle.get("entry") or []:
        resource = entry.get("resource") or {}
        if resource.get("resourceType") != "Immunization":
            continue
        if entry.get("fullUrl", "").endswith(reference) or resource.get("id") == target_id:
            return resource
    return None


def _to_vaccination_dto(immunization: dict[str, Any]) -> VaccinationDto:
    return VaccinationDto(
        id=UUID(immunization["id"]),
        vaccine_name=_vaccine_name(immunization.get("vaccineCode")),
        dose_sequence=_dose_sequence(immunization),
        vaccination_date=_to_local_date(immunization.get("occurrenceDateTime")),
        manufacturer=(immunization.get("manufacturer") or {}).get("display"),
        lot_number=immunization.get("lotNumber"),
        administration_route=_concept_display(immunization.get("route")),
        site_of_administration=_concept_display(immunization.get("site")),
        practitioner=_map_practitioner(immunization),
        reason=_reason_text(immunization),
    )


def _vaccine_name(vaccine_code: dict[str, Any] | None) -> str | None:
    if not vaccine_code:
        return None
    coding = vaccine_code.get("coding") or []
    if coding:
        return coding[0].get("display")
    return vaccine_code.get("text")


def _concept_display(concept: dict[str, Any] | None) -> str | None:
    if not concept:
        return None
    coding = concept.get("coding") or []
    if coding:
        return coding[0].get("display")
    return concept.get("text")


def _to_local_date(value: str | None) -> date | None:
    if not value:
        return None
    # FHIR dateTime allows date, datetime, or datetime+tz; isoformat handles all three.
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).date()
    except ValueError:
        # Fallback for plain dates (YYYY-MM-DD).
        return date.fromisoformat(value[:10])


def _dose_sequence(immunization: dict[str, Any]) -> str | None:
    protocol_applied = immunization.get("protocolApplied") or []
    if not protocol_applied:
        return None
    pa = protocol_applied[0]
    dose = pa.get("doseNumberPositiveInt")
    if dose is None:
        return None
    series = pa.get("seriesDosesPositiveInt")
    return f"{dose}/{series}" if series is not None else str(dose)


def _map_practitioner(immunization: dict[str, Any]) -> PractitionerDto | None:
    performers = immunization.get("performer") or []
    if not performers:
        return None
    actor = performers[0].get("actor") or {}
    if not actor:
        return None
    name = actor.get("display")
    gln = (actor.get("identifier") or {}).get("value")
    if not name:
        return None
    return PractitionerDto(doctor_name=name, gln=gln)


def _reason_text(immunization: dict[str, Any]) -> str | None:
    reason_codes = immunization.get("reasonCode") or []
    if not reason_codes:
        return None
    reason = reason_codes[0]
    if reason.get("text"):
        return reason["text"]
    coding = reason.get("coding") or []
    if coding:
        return coding[0].get("display")
    return None
