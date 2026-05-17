package ch.vacd.pbll.bootstrap

import ch.vacd.pbll.clients.EhrbaseClient
import ch.vacd.pbll.clients.OpenFhirClient
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

enum class BootstrapState { PENDING, OPT_EHRBASE_DONE, OPT_OPENFHIR_DONE, CONTEXT_DONE, MODEL_DONE, READY, FAILED }

/**
 * One-shot, idempotent bootstrap.
 *
 * Order:
 *  1. Wait for openFHIR + EHRbase reachable
 *  2. Upload OPT to EHRbase (skip if template_id already present)
 *  3. Upload OPT to openFHIR (skip if already listed)
 *  4. Upload FHIRconnect context YAML (skip if name already listed)
 *  5. Upload FHIRconnect model YAML (skip if name already listed)
 *  6. Smoke test toopenehr — left to ingest-time
 */
class Bootstrap(
    private val openFhir: OpenFhirClient,
    private val cdr: EhrbaseClient,
    private val templateId: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    val state: AtomicReference<BootstrapState> = AtomicReference(BootstrapState.PENDING)
    val lastError: AtomicReference<String?> = AtomicReference(null)

    private fun read(path: String): String =
        Bootstrap::class.java.classLoader.getResourceAsStream(path)?.bufferedReader()?.readText()
            ?: throw RuntimeException("classpath resource not found: $path")

    suspend fun run() {
        try {
            waitFor()
            uploadOptToEhrbase()
            state.set(BootstrapState.OPT_EHRBASE_DONE)
            uploadOptToOpenFhir()
            state.set(BootstrapState.OPT_OPENFHIR_DONE)
            uploadContext()
            state.set(BootstrapState.CONTEXT_DONE)
            uploadModel()
            state.set(BootstrapState.MODEL_DONE)
            state.set(BootstrapState.READY)
            log.info("Bootstrap READY")
        } catch (t: Throwable) {
            log.error("Bootstrap failed", t)
            lastError.set(t.message ?: t::class.simpleName)
            state.set(BootstrapState.FAILED)
        }
    }

    private suspend fun waitFor() {
        repeat(60) { attempt ->
            try {
                val (s, body) = openFhir.health()
                if (s.value == 200 && body.contains("UP", ignoreCase = true)) {
                    // ping ehrbase too
                    cdr.listTemplates()
                    return
                }
            } catch (_: Throwable) { /* retry */ }
            log.info("waiting for openFHIR + EHRbase (attempt {})", attempt + 1)
            delay(2_000)
        }
        throw RuntimeException("openFHIR / EHRbase not reachable after 120s")
    }

    private suspend fun uploadOptToEhrbase() {
        val existing = cdr.listTemplates()
        val already = existing.any { entry ->
            val obj = entry as? JsonObject ?: return@any false
            val tid = (obj["template_id"] as? JsonPrimitive)?.content
            tid == templateId
        }
        if (already) {
            log.info("EHRbase: template '{}' already present, skipping", templateId)
            return
        }
        val xml = read("bootstrap/ch-vacd-immunization-administration.v1-alpha.opt")
        cdr.uploadOpt(xml)
        log.info("EHRbase: uploaded OPT '{}'", templateId)
    }

    private suspend fun uploadOptToOpenFhir() {
        val existing = openFhir.listOpts()
        val normalized = templateId.replace(' ', '_')
        val already = existing.any { entry ->
            val obj = entry as? JsonObject ?: return@any false
            val candidates = listOfNotNull(
                (obj["templateId"] as? JsonPrimitive)?.content,
                (obj["originalTemplateId"] as? JsonPrimitive)?.content,
                (obj["displayTemplateId"] as? JsonPrimitive)?.content,
                (obj["template_id"] as? JsonPrimitive)?.content,
                ((obj["metadata"] as? JsonObject)?.get("name") as? JsonPrimitive)?.content,
            )
            templateId in candidates || normalized in candidates
        }
        if (already) {
            log.info("openFHIR: template '{}' already present, skipping", templateId)
            return
        }
        val xml = read("bootstrap/ch-vacd-immunization-administration.v1-alpha.opt")
        openFhir.postOpt(xml)
        log.info("openFHIR: uploaded OPT '{}'", templateId)
    }

    private fun yamlName(entry: JsonObject): String? =
        ((entry["metadata"] as? JsonObject)?.get("name") as? JsonPrimitive)?.content
            ?: (entry["name"] as? JsonPrimitive)?.content

    private suspend fun uploadContext() {
        val existing = openFhir.listContexts()
        val already = existing.any { (it as? JsonObject)?.let(::yamlName) == "ch-vacd-immunization.context" }
        if (already) {
            log.info("openFHIR: context already present, skipping")
            return
        }
        val yaml = read("bootstrap/ch-vacd-immunization.context.yml")
        openFhir.postContextYaml(yaml)
        log.info("openFHIR: uploaded context")
    }

    private suspend fun uploadModel() {
        val existing = openFhir.listModels()
        val already = existing.any { (it as? JsonObject)?.let(::yamlName) == "ACTION.medication.v1" }
        if (already) {
            log.info("openFHIR: model already present, skipping")
            return
        }
        val yaml = read("bootstrap/ACTION.medication.v1.yml")
        openFhir.postModelYaml(yaml)
        log.info("openFHIR: uploaded model")
    }
}
