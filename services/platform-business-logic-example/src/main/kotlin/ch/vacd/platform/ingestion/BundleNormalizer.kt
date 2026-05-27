package ch.vacd.platform.ingestion

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Rewrites urn:uuid:-style document Bundles for HAPI FHIR storage.
 *
 * HAPI's JSON parser unconditionally copies fullUrl into each resource's
 * id field.  When fullUrl is urn:uuid:xxx the resulting id fails the
 * id-primitive XSD pattern ([A-Za-z0-9\-\.]{1,64}).  The only way to
 * prevent this is to rewrite fullUrl itself — setting id alone doesn't
 * help because HAPI overwrites it.
 *
 * What changes:
 *   fullUrl   urn:uuid:xxx       -> Type/xxx
 *   id        (absent)           -> xxx
 *   reference urn:uuid:xxx       -> Type/xxx
 */
object BundleNormalizer {

    fun normalize(bundle: JsonObject): JsonObject {
        val entries = bundle["entry"] as? JsonArray ?: return bundle

        // Pass 1: build a map from urn:uuid:xxx -> Type/stripped-uuid
        val rewriteMap = HashMap<String, String>()
        for (entry in entries) {
            val e = entry as? JsonObject ?: continue
            val fullUrl = (e["fullUrl"] as? JsonPrimitive)?.content ?: continue
            val resource = e["resource"] as? JsonObject ?: continue
            val rtype = (resource["resourceType"] as? JsonPrimitive)?.content ?: continue
            if (fullUrl.startsWith("urn:uuid:")) {
                val uuid = fullUrl.removePrefix("urn:uuid:")
                rewriteMap[fullUrl] = "$rtype/$uuid"
            }
        }

        if (rewriteMap.isEmpty()) return bundle

        // Pass 2: rewrite entries — set id on resources, fix fullUrls
        val newEntries = entries.map { entry ->
            val e = entry as? JsonObject ?: return@map entry
            val fullUrl = (e["fullUrl"] as? JsonPrimitive)?.content
            val resource = e["resource"] as? JsonObject ?: return@map entry
            val rtype = (resource["resourceType"] as? JsonPrimitive)?.content

            val newResource = if (fullUrl != null && fullUrl.startsWith("urn:uuid:")) {
                val uuid = fullUrl.removePrefix("urn:uuid:")
                val patched = LinkedHashMap<String, JsonElement>()
                patched["resourceType"] = resource["resourceType"]!!
                patched["id"] = JsonPrimitive(uuid)
                for ((k, v) in resource) {
                    if (k != "resourceType" && k != "id") patched[k] = v
                }
                JsonObject(patched)
            } else resource

            val rewrittenResource = rewriteReferences(newResource, rewriteMap)

            val newEntry = LinkedHashMap<String, JsonElement>()
            for ((k, v) in e) {
                when (k) {
                    "fullUrl" -> {
                        val mapped = fullUrl?.let { rewriteMap[it] }
                        newEntry[k] = if (mapped != null) JsonPrimitive(mapped) else v
                    }
                    "resource" -> newEntry[k] = rewrittenResource
                    else -> newEntry[k] = v
                }
            }
            JsonObject(newEntry)
        }

        val out = LinkedHashMap<String, JsonElement>()
        for ((k, v) in bundle) {
            if (k == "entry") out[k] = JsonArray(newEntries)
            else out[k] = v
        }
        return JsonObject(out)
    }

    private fun rewriteReferences(element: JsonElement, map: Map<String, String>): JsonElement =
        when (element) {
            is JsonObject -> {
                val refVal = (element["reference"] as? JsonPrimitive)?.content
                if (refVal != null && map.containsKey(refVal)) {
                    val patched = LinkedHashMap<String, JsonElement>()
                    for ((k, v) in element) {
                        patched[k] = if (k == "reference") JsonPrimitive(map[refVal]!!)
                        else rewriteReferences(v, map)
                    }
                    JsonObject(patched)
                } else {
                    val patched = LinkedHashMap<String, JsonElement>()
                    for ((k, v) in element) {
                        patched[k] = rewriteReferences(v, map)
                    }
                    JsonObject(patched)
                }
            }
            is JsonArray -> JsonArray(element.map { rewriteReferences(it, map) })
            else -> element
        }
}
