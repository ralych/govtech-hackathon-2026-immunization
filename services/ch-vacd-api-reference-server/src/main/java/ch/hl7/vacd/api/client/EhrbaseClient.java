package ch.hl7.vacd.api.client;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public interface EhrbaseClient {


	/**
	 * List all ADL 1.4 templates registered in EHRbase.
	 */
	JsonNode listTemplates();

	/**
	 * Upload an OPT (Operational Template) XML to EHRbase.
	 */
	String uploadOpt(String xml);

	/**
	 * Find an EHR linked to the given FHIR Patient id via
	 * EHR_STATUS.subject.external_ref.
	 */
	String findEhrByPatient(String patientId);

	/**
	 * Create a new EHR with subject linked to the given FHIR Patient id.
	 */
	String createEhr(String patientId);

	/**
	 * Find an existing EHR for the patient, or create one if none exists.
	 */
	String findOrCreateEhr(String patientId);

	/**
	 * POST a FLAT-format Composition to the given EHR. Returns the composition uid.
	 */
	String postCompositionFlat(String ehrId, String flatBody, String templateId);

	String getCompositionFlat(String ehrId, String compositionUid);

	/**
	 * Execute an AQL query and return the parsed rows. Each row is a JsonNode
	 * array.
	 */
	List<JsonNode> executeAql(String aql);

	/**
	 * Method to retrieve all Immunization resources for a given EHR id. This is
	 * used in the read-path to build the vaccination record document.
	 * 
	 * @param ehrId the EHR id to retrieve the immunizations for
	 * @return a list of Immunization resources in JSON format
	 */
	String getImmunizations(String ehrId);

}