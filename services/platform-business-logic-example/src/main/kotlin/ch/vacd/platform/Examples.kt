package ch.vacd.platform

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

/**
 * Discovers CH VACD example Bundles from a directory on disk.
 *
 * Strict by construction: only files matching the
 * `*-immunization-administration-*.json` pattern are listed. The actual
 * shape (Bundle / type=document / Composition as entry[0] / Immunization
 * in a section) is enforced by `BundleExtractor` at ingest time, not here —
 * we don't want this catalogue to become a second-tier validator.
 *
 * The directory is the repo's top-level `examples/`, mounted into the
 * runtime via the `EXAMPLES_DIR` env var. The platform example refuses to start if
 * the directory doesn't exist or contains no examples (better fail fast
 * at boot than 404 from the demo UI later).
 */
class Examples(private val dir: Path) {

    fun list(): List<Pair<String, String>> =
        Files.list(dir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.name.endsWith(".json") }
                .filter { it.name.contains("immunizationadministration") }
                .sorted()
                .map { it.name.removeSuffix(".json") to readableLabel(it.name) }
                .toList()
        }

    fun read(slug: String): String? {
        val p = dir.resolve("$slug.json")
        return if (p.exists()) Files.readString(p) else null
    }

    private fun readableLabel(filename: String): String {
        val stem = filename.removeSuffix(".json")
        val uuid = stem.substringAfterLast("-")
        val shortId = uuid.take(8)
        return "CH VACD Immunization Administration ($shortId)"
    }

    companion object {
        /**
         * Resolve the examples directory from the env var, with a development
         * fallback to the repo-relative path (works when the platform example is run from
         * the dev container without env config).
         */
        fun fromEnvOrDevDefault(envVar: String = "EXAMPLES_DIR"): Examples {
            val configured = System.getenv(envVar)
            val candidates = buildList {
                if (configured != null) add(Path.of(configured))
                add(Path.of("../../examples"))  // run from services/platform-business-logic-example/
                add(Path.of("./examples"))      // run from repo root
                add(Path.of("/workspaces/govtech-hackathon-2026-immunization/examples"))
            }
            val dir = candidates.firstOrNull { it.exists() && Files.isDirectory(it) }
                ?: throw IllegalStateException(
                    "Examples directory not found. Tried: $candidates. " +
                    "Set $envVar to the absolute path of the repo's examples/ directory."
                )
            return Examples(dir.toAbsolutePath().normalize())
        }
    }
}
