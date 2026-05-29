package ch.hl7.vacd.api.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per Konkretisierung §13, the original FHIR JSON MUST be retained verbatim
 * inside the Composition's feeder_audit/original_content.
 *
 * The platform talks to EHRbase in FLAT format; in FLAT, feeder_audit lives under
 * the template key prefix with /_feeder_audit/... path.
 *
 * We populate:
 *   {tplKey}/_feeder_audit/originating_system_audit|system_id
 *   {tplKey}/_feeder_audit/originating_system_audit|version_id
 *   {tplKey}/_feeder_audit/originating_system_audit|time
 *   {tplKey}/_feeder_audit/original_content
 *   {tplKey}/_feeder_audit/original_content|formalism
 */
public class FeederAuditEnricher {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String SYSTEM_ID = "ch-vacd-fhir";
	private static final String COMPOSER_NAME = "CH VACD Platform";
	private static final String LANGUAGE = "en";
	private static final String TERRITORY = "CH";
	private static final String CATEGORY = "433|event";

	/**
	 * Enrich a FLAT JSON with composition-level metadata and feeder_audit fields.
	 *
	 * @param flatJson         the FLAT JSON string from openFHIR
	 * @param originalFhirJson the original FHIR Bundle JSON to embed
	 * @return enriched FLAT JSON string
	 */
	public static String addOriginal(String flatJson, String originalFhirJson) {
		try {
			ObjectNode flat = (ObjectNode) MAPPER.readTree(flatJson);
			String time = OffsetDateTime.now().toString();

			// Composition-level metadata that EHRbase requires but openFHIR doesn't emit.
			putIfAbsent(flat, "ctx/language", LANGUAGE);
			putIfAbsent(flat, "ctx/territory", TERRITORY);
			putIfAbsent(flat, "ctx/composer_name", COMPOSER_NAME);
			putIfAbsent(flat, "ctx/category", CATEGORY);
			putIfAbsent(flat, "ctx/time", time);

			// Determine template key prefix from the first key that contains a '/'
			String tplKey = findTemplateKeyPrefix(flat);
			if (tplKey != null && !tplKey.isEmpty()) {
				putIfAbsent(flat, tplKey + "/_feeder_audit/originating_system_audit|system_id", SYSTEM_ID);
				putIfAbsent(flat, tplKey + "/_feeder_audit/originating_system_audit|version_id", "1");
				putIfAbsent(flat, tplKey + "/_feeder_audit/originating_system_audit|time", time);
				putIfAbsent(flat, tplKey + "/_feeder_audit/original_content", originalFhirJson);
				putIfAbsent(flat, tplKey + "/_feeder_audit/original_content|formalism", "application/fhir+json");
			}

			// Fix participations: openFHIR's performer mapping emits _other_participation
			// but omits required openEHR RM fields (function, id scheme).
			fixParticipations(flat);

			return MAPPER.writeValueAsString(flat);
		} catch (Exception e) {
			throw new RuntimeException("FeederAuditEnricher failed", e);
		}
	}

	private static String findTemplateKeyPrefix(ObjectNode flat) {
		Iterator<String> keys = flat.fieldNames();
		while (keys.hasNext()) {
			String key = keys.next();
			if (key.contains("/")) {
				int idx = key.indexOf('/');
				return key.substring(0, idx);
			}
		}
		return null;
	}

	private static void putIfAbsent(ObjectNode node, String key, String value) {
		if (!node.has(key)) {
			node.put(key, value);
		}
	}

	private static void fixParticipations(ObjectNode flat) {
		// Find all keys containing _other_participation, collect unique prefixes
		Map<String, Boolean> prefixes = new LinkedHashMap<>();
		Iterator<String> keys = flat.fieldNames();
		while (keys.hasNext()) {
			String key = keys.next();
			if (key.contains("_other_participation")) {
				String prefix = key.contains("|") ? key.substring(0, key.lastIndexOf('|')) : key;
				prefixes.put(prefix, Boolean.TRUE);
			}
		}
		for (String prefix : prefixes.keySet()) {
			putIfAbsent(flat, prefix + "|function", "performer");
			if (!flat.has(prefix + "|id_scheme")) {
				putIfAbsent(flat, prefix + "|id_scheme", "FHIR");
			}
		}
	}
}
