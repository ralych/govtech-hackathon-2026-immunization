package ch.vacd.pbll.clients

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
 * Limitations confirmed live 2026-05-16:
 *  - $validate not supported (HTTP 400).
 *  - Bundle search not supported (HTTP 400).
 *  - identifier search not supported (HTTP 400).
 *  - GET /Patient/{unknown} returns HTTP 200 + stub Patient {family:"Test",given:["Patient"]}.
 *
 * For the tracer bullet we always POST (create); we don't try to upsert by identifier.
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
