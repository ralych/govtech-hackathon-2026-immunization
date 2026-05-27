package ch.vacd.platform.clients

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Thin client for fhir-server-1 (HAPI 8.8.1, CH VACD reference server).
 *
 * The server validates incoming resources against CH VACD IG profiles via
 * RequestValidatingInterceptor. POST /Bundle stores the Bundle itself and
 * extracts each entry resource so they are individually searchable.
 */
class HapiClient(private val baseUrl: String) {
    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 30_000
        }
        expectSuccess = false
    }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun createResource(resourceType: String, body: JsonObject): String {
        val resp = http.post("$baseUrl/$resourceType") {
            contentType(ContentType.parse("application/fhir+json"))
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        val text = resp.bodyAsText()
        if (!resp.status.isSuccess()) {
            throw RuntimeException("HAPI POST /$resourceType failed ${resp.status.value}: $text")
        }
        // Prefer Location header, else parsed id.
        resp.headers["Location"]?.let { loc ->
            val parts = loc.split("/")
            val idx = parts.indexOf(resourceType)
            if (idx >= 0 && idx + 1 < parts.size) return parts[idx + 1]
        }
        val parsed = json.parseToJsonElement(text) as? JsonObject
        val id = (parsed?.get("id") as? JsonPrimitive)?.content
        return id ?: throw RuntimeException("HAPI POST /$resourceType: no id in response: $text")
    }

    suspend fun postBundle(body: String): JsonObject {
        val resp = http.post("$baseUrl/Bundle") {
            contentType(ContentType.parse("application/fhir+json"))
            setBody(body)
        }
        val text = resp.bodyAsText()
        if (!resp.status.isSuccess()) {
            throw RuntimeException("HAPI POST /Bundle failed ${resp.status.value}: $text")
        }
        return json.parseToJsonElement(text) as JsonObject
    }

    suspend fun get(resourceType: String, id: String): Pair<HttpStatusCode, String> {
        val resp = http.get("$baseUrl/$resourceType/$id")
        return resp.status to resp.bodyAsText()
    }

    suspend fun metadata(): String = http.get("$baseUrl/metadata").bodyAsText()

    /** Strips a resource-level `id` so HAPI assigns one fresh. */
    fun stripId(obj: JsonObject): JsonObject = buildJsonObject {
        obj.forEach { (k, v) -> if (k != "id") put(k, v) }
    }

    fun close() = http.close()
}
