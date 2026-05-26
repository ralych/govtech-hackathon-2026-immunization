package ch.vacd.platform.clients

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add

/**
 * Thin client for EHRbase 2.31.0 over the openEHR REST API.
 *
 * Auth: BASIC ehrbase-user:SuperSecretPassword (dev creds).
 * Namespace constraint: subject.external_ref.namespace must match
 *   [a-zA-Z][a-zA-Z0-9-_:/&+?]* — dots are rejected. We use "ch-vacd".
 */
class EhrbaseClient(
    private val baseUrl: String,
    private val user: String,
    private val pass: String,
) {
    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 60_000
        }
        install(Auth) {
            basic {
                credentials { BasicAuthCredentials(user, pass) }
                sendWithoutRequest { true }
            }
        }
        expectSuccess = false
    }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun listTemplates(): JsonArray {
        val r = http.get("$baseUrl/definition/template/adl1.4") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        val t = r.bodyAsText()
        if (!r.status.isSuccess()) throw RuntimeException("EHRbase GET templates ${r.status.value}: $t")
        return json.parseToJsonElement(t) as? JsonArray ?: JsonArray(emptyList())
    }

    suspend fun uploadOpt(xml: String): String {
        val r = http.post("$baseUrl/definition/template/adl1.4") {
            contentType(ContentType.parse("application/xml"))
            setBody(xml)
        }
        val t = r.bodyAsText()
        if (!r.status.isSuccess()) throw RuntimeException("EHRbase upload OPT ${r.status.value}: $t")
        return t
    }

    /** Fetch the canonical empty FLAT example skeleton for the named template. */
    suspend fun flatExample(templateId: String): String {
        val tid = templateId.encodeURLParameter()
        val r = http.get("$baseUrl/definition/template/adl1.4/$tid/example?format=FLAT") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        if (!r.status.isSuccess()) throw RuntimeException("EHRbase FLAT example ${r.status.value}")
        return r.bodyAsText()
    }

    /**
     * Find an EHR linked to the given FHIR Patient id (via EHR_STATUS.subject.external_ref).
     * Returns the ehrId if found, else null.
     */
    suspend fun findEhrByPatient(patientId: String, namespace: String = "ch-vacd"): String? {
        val aql = """
            SELECT e/ehr_id/value
            FROM EHR e
            WHERE e/ehr_status/subject/external_ref/id/value = '$patientId'
              AND e/ehr_status/subject/external_ref/namespace = '$namespace'
        """.trimIndent()
        val body = buildJsonObject { put("q", aql) }
        val r = http.post("$baseUrl/query/aql") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        val text = r.bodyAsText()
        if (!r.status.isSuccess()) throw RuntimeException("EHRbase AQL ${r.status.value}: $text")
        val parsed = json.parseToJsonElement(text) as? JsonObject ?: return null
        val rows = parsed["rows"] as? JsonArray ?: return null
        val first = rows.firstOrNull() as? JsonArray ?: return null
        return (first.firstOrNull() as? JsonPrimitive)?.content
    }

    suspend fun createEhr(patientId: String, namespace: String = "ch-vacd"): String {
        val status = buildJsonObject {
            put("_type", "EHR_STATUS")
            put("archetype_node_id", "openEHR-EHR-EHR_STATUS.generic.v1")
            put("name", buildJsonObject { put("value", "EHR Status") })
            put("subject", buildJsonObject {
                put("external_ref", buildJsonObject {
                    put("id", buildJsonObject {
                        put("_type", "GENERIC_ID")
                        put("value", patientId)
                        put("scheme", "fhir")
                    })
                    put("namespace", namespace)
                    put("type", "PERSON")
                })
            })
            put("is_queryable", true)
            put("is_modifiable", true)
        }
        val r = http.post("$baseUrl/ehr") {
            contentType(ContentType.Application.Json)
            header("Prefer", "return=representation")
            setBody(json.encodeToString(JsonObject.serializer(), status))
        }
        val text = r.bodyAsText()
        if (!r.status.isSuccess()) throw RuntimeException("EHRbase create EHR ${r.status.value}: $text")
        val parsed = json.parseToJsonElement(text) as? JsonObject
        val ehrId = (parsed?.get("ehr_id") as? JsonObject)?.get("value") as? JsonPrimitive
        return ehrId?.content
            ?: r.headers["ETag"]
            ?: r.headers["Location"]?.substringAfterLast("/")?.trim('"', ' ')
            ?: throw RuntimeException("create EHR: no ehrId in response: $text")
    }

    suspend fun findOrCreateEhr(patientId: String, namespace: String = "ch-vacd"): String =
        findEhrByPatient(patientId, namespace) ?: createEhr(patientId, namespace)

    /**
     * POST a FLAT-format Composition to the given EHR. Returns the composition uid.
     *
     * EHRbase 2.31's flat encoder needs the templateId either inferred from the
     * first segment of every key (must match OPT template_id normalised) or
     * passed as a query parameter. We always pass it to be explicit.
     */
    suspend fun postCompositionFlat(ehrId: String, flatBody: String, templateId: String? = null): String {
        val tidPart = if (templateId != null) "&templateId=${templateId.encodeURLParameter()}" else ""
        val r = http.post("$baseUrl/ehr/$ehrId/composition?format=FLAT$tidPart") {
            contentType(ContentType.Application.Json)
            header("Prefer", "return=representation")
            setBody(flatBody)
        }
        val text = r.bodyAsText()
        if (!r.status.isSuccess()) {
            throw RuntimeException("EHRbase POST composition ${r.status.value}: ${text.take(800)}")
        }
        // Prefer compositionUid from response or ETag.
        val parsed = json.parseToJsonElement(text)
        if (parsed is JsonObject) {
            val flatUid = (parsed["ctx/composition_uid"] as? JsonPrimitive)?.content
                ?: (parsed["compositionUid"] as? JsonPrimitive)?.content
                ?: (parsed["uid"] as? JsonObject)?.get("value")?.let { (it as? JsonPrimitive)?.content }
            if (flatUid != null) return flatUid
        }
        r.headers["ETag"]?.let { return it.trim('"', ' ') }
        r.headers["Location"]?.let { return it.substringAfterLast("/").trim('"', ' ') }
        throw RuntimeException("EHRbase POST composition: no uid in response: ${text.take(400)}")
    }

    /** Fetch a Composition as canonical (structured) JSON. */
    suspend fun getCompositionCanonical(ehrId: String, uid: String): String {
        val r = http.get("$baseUrl/ehr/$ehrId/composition/$uid")
        val t = r.bodyAsText()
        if (!r.status.isSuccess()) throw RuntimeException("EHRbase GET composition ${r.status.value}: $t")
        return t
    }

    suspend fun aql(query: String): String {
        val body = buildJsonObject { put("q", query) }
        val r = http.post("$baseUrl/query/aql") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        val t = r.bodyAsText()
        if (!r.status.isSuccess()) throw RuntimeException("EHRbase AQL ${r.status.value}: $t")
        return t
    }

    fun close() = http.close()
}
