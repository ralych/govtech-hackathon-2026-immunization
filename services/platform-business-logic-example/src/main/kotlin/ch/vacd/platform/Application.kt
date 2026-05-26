package ch.vacd.platform

import ch.vacd.platform.bootstrap.Bootstrap
import ch.vacd.platform.clients.EhrbaseClient
import ch.vacd.platform.clients.HapiClient
import ch.vacd.platform.clients.OpenFhirClient
import ch.vacd.platform.ingestion.ImmunizationIngest
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val appJson = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
    encodeDefaults = true
}

val PrettyJson: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val log = LoggerFactory.getLogger("ch.vacd.platform.Application")
    val cfg = loadConfig()
    log.info("Platform example starting; FHIR={} MAPPER={} CDR={} TEMPLATE='{}'",
        cfg.fhirServer1Url, cfg.mapperUrl, cfg.cdrUrl, cfg.templateId)

    install(DefaultHeaders) {
        header("X-Platform-Example", "ch-vacd-platform-example/0.1.0")
    }
    install(CallLogging)
    install(ContentNegotiation) { json(appJson) }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("unhandled error", cause)
            val msg = cause.message ?: cause::class.simpleName ?: "unknown error"
            val diag = kotlinx.serialization.json.JsonPrimitive(msg).toString()
            call.respondText(
                """{"resourceType":"OperationOutcome","issue":[{"severity":"error","code":"exception","diagnostics":$diag}]}""",
                io.ktor.http.ContentType.Application.Json,
                HttpStatusCode.InternalServerError,
            )
        }
    }

    val hapi = HapiClient(cfg.fhirServer1Url)
    val openFhir = OpenFhirClient(cfg.mapperUrl, cfg.templateId)
    val cdr = EhrbaseClient(cfg.cdrUrl, cfg.cdrUser, cfg.cdrPass)
    val ingest = ImmunizationIngest(hapi, openFhir, cdr, cfg.templateId)
    val examples = Examples.fromEnvOrDevDefault()
    log.info("Examples directory: {} ({} files)", examples.javaClass.simpleName, examples.list().size)

    // Fire-and-forget bootstrap. The /healthz endpoint exposes its state.
    val bootstrap = Bootstrap(openFhir, cdr, cfg.templateId)
    launch { bootstrap.run() }

    routes(cfg, hapi, openFhir, cdr, ingest, bootstrap, examples)
}
