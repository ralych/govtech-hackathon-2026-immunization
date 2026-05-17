package ch.vacd.pbll

import io.ktor.server.application.Application

data class PbllConfig(
    val fhirServer1Url: String,
    val mapperUrl: String,
    val cdrUrl: String,
    val cdrUser: String,
    val cdrPass: String,
    val templateId: String,
)

fun Application.loadConfig(): PbllConfig {
    val c = environment.config.config("pbll")
    return PbllConfig(
        fhirServer1Url = c.property("fhirServer1Url").getString(),
        mapperUrl = c.property("mapperUrl").getString(),
        cdrUrl = c.property("cdrUrl").getString(),
        cdrUser = c.property("cdrUser").getString(),
        cdrPass = c.property("cdrPass").getString(),
        templateId = c.property("templateId").getString(),
    )
}
