package ch.vacd.platform

import ch.vacd.platform.bootstrap.Bootstrap
import ch.vacd.platform.clients.EhrbaseClient
import ch.vacd.platform.clients.HapiClient
import ch.vacd.platform.clients.OpenFhirClient
import ch.vacd.platform.ingestion.FeederAuditEnricher
import ch.vacd.platform.ingestion.ImmunizationIngest
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Hits the live compose stack. Enabled only when INTEGRATION=1.
 * Reads service URLs from env (falls back to service-name URLs) and loads
 * the canonical CH VACD examples from the repo's top-level examples/.
 */
class EndToEndIntegrationTest {
    private val fhirUrl = System.getenv("FHIR_SERVER_1_URL")
        ?: "http://fhir-server-1:9111/ch-vacd-api-reference-server/fhir"
    private val mapperUrl = System.getenv("MAPPER_URL") ?: "http://openfhir:8083"
    private val cdrUrl = System.getenv("CDR_URL") ?: "http://ehrbase:8080/ehrbase/rest/openehr/v1"
    private val templateId = "ch-vacd-immunization administration.v1-alpha"

    private fun examplesDir(): Path {
        val candidates = listOf(
            Path.of("../../examples"),
            Path.of("examples"),
            Path.of("/workspaces/govtech-hackathon-2026-immunization/examples"),
        )
        return candidates.firstOrNull { Files.exists(it) && Files.isDirectory(it) }
            ?: fail("examples dir not found in $candidates")
    }

    private fun example(slug: String): JsonElement {
        val body = Files.readString(examplesDir().resolve("$slug.json"))
        return Json.parseToJsonElement(body)
    }

    private fun ingestor(): Triple<ImmunizationIngest, EhrbaseClient, Bootstrap> {
        val hapi = HapiClient(fhirUrl)
        val openFhir = OpenFhirClient(mapperUrl, templateId)
        val cdr = EhrbaseClient(cdrUrl, "ehrbase-user", "SuperSecretPassword")
        val bs = Bootstrap(openFhir, cdr, templateId)
        runBlocking { bs.run() }
        return Triple(ImmunizationIngest(hapi, openFhir, cdr), cdr, bs)
    }

    @Test fun `ingests all canonical CH VACD document Bundles end-to-end`() = runBlocking {
        val (ingest, cdr, _) = ingestor()
        val slugs = listOf(
            "01-immunization-administration-boostrix",
            "02-immunization-administration-comirnaty",
            "03-immunization-administration-priorix",
        )
        for (slug in slugs) {
            val result = ingest.ingest(example(slug))
            assertNotNull(result.compositionUid, "$slug: compositionUid")
            assertNotNull(result.ehrId, "$slug: ehrId")
            // Each canonical Bundle carries 1 Practitioner and 1 Organization.
            assertEquals(1, result.practitionerIds.size, "$slug: practitioners")
            assertEquals(1, result.organizationIds.size, "$slug: organizations")
            val canonical = cdr.getCompositionCanonical(result.ehrId, result.compositionUid)
            assertTrue(canonical.contains("ACTION"), "$slug: composition missing ACTION")
            val parsed = Json.parseToJsonElement(canonical) as kotlinx.serialization.json.JsonObject
            val orig = FeederAuditEnricher.extractOriginal(parsed)
            assertNotNull(orig, "$slug: feeder_audit/original_content missing")
            assertTrue(orig.contains("\"Immunization\""), "$slug: original_content not FHIR JSON")
        }
    }
}
