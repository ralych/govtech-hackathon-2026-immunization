package ch.vacd.pbll.ingestion

import ch.vacd.pbll.PrettyJson
import ch.vacd.pbll.clients.EhrbaseClient
import ch.vacd.pbll.clients.HapiClient
import ch.vacd.pbll.clients.OpenFhirClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

private const val CH_VACD_PROFILE = "http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-immunization"

data class IngestResult(
    val compositionUid: String,
    val ehrId: String,
    val patientId: String,
    val practitionerIds: List<String>,
    val organizationIds: List<String>,
    val intermediateFlat: JsonObject,
    val enrichedFlat: JsonObject,
    val originalFhirJson: String,
)

class ImmunizationIngest(
    private val hapi: HapiClient,
    private val openFhir: OpenFhirClient,
    private val cdr: EhrbaseClient,
    private val templateId: String = "ch-vacd-immunization administration.v1-alpha",
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun ingest(input: JsonElement): IngestResult {
        val peeled = BundleExtractor.peel(input)
        validateStatus(peeled.immunization)

        val patientId = hapi.createResource("Patient", hapi.stripId(peeled.patient))
        val practitionerIds = peeled.practitioners.map { hapi.createResource("Practitioner", hapi.stripId(it)) }
        val organizationIds = peeled.organizations.map { hapi.createResource("Organization", hapi.stripId(it)) }

        val ehrId = cdr.findOrCreateEhr(patientId)

        // Pin the FHIR resource to point at the HAPI-assigned Patient.id so any
        // downstream re-hydration would resolve correctly. Also ensure the
        // CH VACD profile is declared in meta.profile — openFHIR uses that to
        // pick the right FHIRconnect context.
        val pinned = ensureProfile(pinPatientRef(peeled.immunization, patientId))
        val pinnedText = PrettyJson.encodeToString(JsonObject.serializer(), pinned)

        val flat = openFhir.toOpenEhr(pinnedText, flat = true)
        val enriched = FeederAuditEnricher.addOriginal(flat, pinnedText)

        val flatText = PrettyJson.encodeToString(JsonObject.serializer(), enriched)
        val compositionUid = cdr.postCompositionFlat(ehrId, flatText, templateId)
        log.info("Stored Composition uid={} ehrId={} patientId={}", compositionUid, ehrId, patientId)

        return IngestResult(
            compositionUid = compositionUid,
            ehrId = ehrId,
            patientId = patientId,
            practitionerIds = practitionerIds,
            organizationIds = organizationIds,
            intermediateFlat = flat,
            enrichedFlat = enriched,
            originalFhirJson = pinnedText,
        )
    }

    private fun validateStatus(imm: JsonObject) {
        val s = (imm["status"] as? JsonPrimitive)?.content
        if (s != "completed") {
            throw UnprocessableException("Immunization.status must be 'completed' for the tracer bullet (was '$s')")
        }
    }

    private fun pinPatientRef(imm: JsonObject, patientId: String): JsonObject {
        val patientNode = buildJsonObject { put("reference", "Patient/$patientId") }
        val out = LinkedHashMap<String, JsonElement>()
        out.putAll(imm)
        out["patient"] = patientNode
        return JsonObject(out)
    }

    private fun ensureProfile(imm: JsonObject): JsonObject {
        val meta = imm["meta"] as? JsonObject
        val profiles = (meta?.get("profile") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
        if (profiles != null && profiles.contains(CH_VACD_PROFILE)) return imm
        val newMeta = buildJsonObject {
            meta?.forEach { (k, v) -> if (k != "profile") put(k, v) }
            put("profile", buildJsonArray {
                add(JsonPrimitive(CH_VACD_PROFILE))
                profiles?.filter { it != CH_VACD_PROFILE }?.forEach { add(JsonPrimitive(it)) }
            })
        }
        val out = LinkedHashMap<String, JsonElement>()
        out.putAll(imm)
        out["meta"] = newMeta
        return JsonObject(out)
    }
}

class UnprocessableException(message: String) : RuntimeException(message)
