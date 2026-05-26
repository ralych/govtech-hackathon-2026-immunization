package ch.vacd.platform

import io.ktor.server.application.Application

data class PlatformConfig(
    val fhirServer1Url: String,
    val mapperUrl: String,
    val cdrUrl: String,
    val cdrUser: String,
    val cdrPass: String,
    val templateId: String,
)

fun Application.loadConfig(): PlatformConfig {
    val c = environment.config.config("platform")
    return PlatformConfig(
        fhirServer1Url = c.property("fhirServer1Url").getString(),
        mapperUrl = c.property("mapperUrl").getString(),
        cdrUrl = c.property("cdrUrl").getString(),
        cdrUser = c.property("cdrUser").getString(),
        cdrPass = c.property("cdrPass").getString(),
        templateId = c.property("templateId").getString(),
    )
}
