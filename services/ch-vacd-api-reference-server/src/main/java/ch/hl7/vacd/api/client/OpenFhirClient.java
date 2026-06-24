package ch.hl7.vacd.api.client;

import com.fasterxml.jackson.databind.JsonNode;

public interface OpenFhirClient {

	/**
	 * Convert a FHIR Bundle JSON to openEHR FLAT format via openFHIR.
	 *
	 * @param fhirJson   the FHIR Bundle JSON string
	 * @param templateId optional template ID override (uses default if null)
	 * @return the FLAT JSON as a string (Jackson-parseable ObjectNode)
	 */
	String toOpenEhr(String fhirJson, String templateId);

	/**
	 * Convert a FHIR Bundle JSON to openEHR FLAT format using the default template ID.
	 */
	String toOpenEhr(String fhirJson);

	/**
	 * Converts a flattened openEHR JSON back to FHIR using openFHIR's /openfhir/tofhir endpoint.
	 * @return the FHIR JSON as a string
	 */
	String toFhir(String flatJson);

	/**
	 * Converts an openEHR JSON (canonical or flat) to FHIR using openFHIR's /openfhir/tofhir endpoint.
	 * @param json       the openEHR JSON string
	 * @param templateId the template ID to use for the conversion
	 * @return the FHIR JSON as a string
	 */
	String toFhir(String json, String templateId);

	/**
	 * Check openFHIR health endpoint.
	 */
	boolean isHealthy();

	/**
	 * List OPTs registered in openFHIR.
	 */
	JsonNode listOpts();

	/**
	 * Upload an OPT XML to openFHIR.
	 */
	String postOpt(String xml);

	/**
	 * List FHIRconnect contexts registered in openFHIR.
	 */
	JsonNode listContexts();

	/**
	 * Upload a FHIRconnect context YAML to openFHIR.
	 */
	String postContextYaml(String yaml);

	/**
	 * List FHIRconnect models registered in openFHIR.
	 */
	JsonNode listModels();

	/**
	 * Upload a FHIRconnect model YAML to openFHIR.
	 */
	String postModelYaml(String yaml);

	/**
	 * Delete a FHIRconnect context by name.
	 */
	boolean deleteContext(String name);

	/**
	 * Delete a FHIRconnect model by name.
	 */
	boolean deleteModel(String name);

}