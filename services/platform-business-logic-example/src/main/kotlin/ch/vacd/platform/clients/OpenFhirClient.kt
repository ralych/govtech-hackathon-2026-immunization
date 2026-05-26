package ch.vacd.platform.clients

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Thin client for openFHIR 2.2.3.
 *
 * Endpoints used:
 *  - GET /health                  → liveness (returns "UP")
 *  - GET/POST /opt                → operational templates
 *  - GET/POST /fc/context         → FHIRconnect context YAMLs
 *  - GET/POST /fc/model           → FHIRconnect model YAMLs
 *  - POST /openfhir/toopenehr     → FHIR → openEHR FLAT JSON conversion
 *
 * Auto-context selection is fragile (upstream #10) — always pass templateId explicitly.
 */
class OpenFhirClient(baseUrl: String, private val defaultTemplateId: String) {
    // Some environments set MAPPER_URL with a trailing `/openfhir` (the path
    // prefix used by /openfhir/toopenehr); strip it so concatenation works.
    private val baseUrl: String = baseUrl
        .trimEnd('/')
        .removeSuffix("/openfhir")
        .trimEnd('/')

    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 60_000
        }
        expectSuccess = false
    }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun health(): Pair<HttpStatusCode, String> {
        val r = http.get("$baseUrl/health")
        return r.status to r.bodyAsText()
    }

    suspend fun listContexts(): JsonArray = listAt("/fc/context")
    suspend fun listModels(): JsonArray = listAt("/fc/model")
    suspend fun listOpts(): JsonArray = listAt("/opt")

    private suspend fun listAt(path: String): JsonArray {
        val r = http.get("$baseUrl$path")
        val text = r.bodyAsText()
        if (!r.status.isSuccess()) throw RuntimeException("GET $path failed ${r.status.value}: $text")
        return json.parseToJsonElement(text) as? JsonArray ?: JsonArray(emptyList())
    }

    suspend fun postOpt(xml: String): String {
        val r = http.post("$baseUrl/opt") {
            contentType(ContentType.parse("application/xml"))
            setBody(xml)
        }
        val text = r.bodyAsText()
        if (!r.status.isSuccess()) throw RuntimeException("POST /opt failed ${r.status.value}: $text")
        return text
    }

    suspend fun postContextYaml(yaml: String): String {
        val r = http.post("$baseUrl/fc/context") {
            contentType(ContentType.parse("application/x-yaml"))
            setBody(yaml)
        }
        val text = r.bodyAsText()
        if (!r.status.isSuccess()) throw RuntimeException("POST /fc/context failed ${r.status.value}: $text")
        return text
    }

    suspend fun postModelYaml(yaml: String): String {
        val r = http.post("$baseUrl/fc/model") {
            contentType(ContentType.parse("application/x-yaml"))
            setBody(yaml)
        }
        val text = r.bodyAsText()
        if (!r.status.isSuccess()) throw RuntimeException("POST /fc/model failed ${r.status.value}: $text")
        return text
    }

    suspend fun deleteContext(name: String): Boolean {
        val r = http.delete("$baseUrl/fc/context/$name")
        return r.status.isSuccess()
    }
    suspend fun deleteModel(name: String): Boolean {
        val r = http.delete("$baseUrl/fc/model/$name")
        return r.status.isSuccess()
    }

    suspend fun toOpenEhr(fhirJson: String, templateId: String = defaultTemplateId, flat: Boolean = true): JsonObject {
        // openFHIR's findContextByTemplateId matches the literal context.template.id
        // string (Mongo: $eq). We pass the unmodified template id; OPT lookup later
        // normalises spaces → underscores internally.
        val tid = templateId.encodeURLParameter()
        val r = http.post("$baseUrl/openfhir/toopenehr?templateId=$tid&flat=$flat") {
            contentType(ContentType.parse("application/fhir+json"))
            setBody(fhirJson)
        }
        val text = r.bodyAsText()
        if (!r.status.isSuccess()) {
            throw RuntimeException("openFHIR toopenehr ${r.status.value}: ${text.take(500)}")
        }
        val parsed = json.parseToJsonElement(text)
        return parsed as? JsonObject ?: throw RuntimeException("openFHIR returned non-object: $text")
    }

    fun close() = http.close()
}
