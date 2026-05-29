package ch.hl7.vacd.api.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Thin client for EHRbase 2.31.0 over the openEHR REST API.
 * Auth: BASIC ehrbase-user:SuperSecretPassword (dev creds).
 */
@Component
public class EhrbaseClient {

	private static final Logger log = LoggerFactory.getLogger(EhrbaseClient.class);
	private static final String NAMESPACE = "ch-vacd";

	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;
	private final String baseUrl;
	private final String authHeader;

	public EhrbaseClient(
			@Value("${ehrbase.url:http://ehrbase:8080/ehrbase/rest/openehr/v1}") String baseUrl,
			@Value("${ehrbase.user:ehrbase-user}") String user,
			@Value("${ehrbase.password:SuperSecretPassword}") String password) {
		this.restTemplate = new RestTemplate();
		this.objectMapper = new ObjectMapper();
		this.baseUrl = baseUrl;
		this.authHeader = "Basic " + Base64.getEncoder()
				.encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
	}

	private HttpHeaders headers(MediaType contentType) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", authHeader);
		headers.setContentType(contentType);
		headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
		return headers;
	}

	/**
	 * List all ADL 1.4 templates registered in EHRbase.
	 */
	public JsonNode listTemplates() {
		try {
			HttpHeaders hdrs = new HttpHeaders();
			hdrs.set("Authorization", authHeader);
			hdrs.set("Accept", MediaType.APPLICATION_JSON_VALUE);
			HttpEntity<Void> request = new HttpEntity<>(hdrs);
			ResponseEntity<String> response = restTemplate.exchange(
					baseUrl + "/definition/template/adl1.4", HttpMethod.GET, request, String.class);
			return objectMapper.readTree(response.getBody());
		} catch (Exception e) {
			log.warn("listTemplates failed: {}", e.getMessage());
			return objectMapper.createArrayNode();
		}
	}

	/**
	 * Upload an OPT (Operational Template) XML to EHRbase.
	 */
	public String uploadOpt(String xml) {
		try {
			HttpHeaders hdrs = new HttpHeaders();
			hdrs.set("Authorization", authHeader);
			hdrs.setContentType(MediaType.APPLICATION_XML);
			HttpEntity<String> request = new HttpEntity<>(xml, hdrs);
			ResponseEntity<String> response = restTemplate.exchange(
					baseUrl + "/definition/template/adl1.4", HttpMethod.POST, request, String.class);
			return response.getBody();
		} catch (HttpClientErrorException e) {
			throw new RuntimeException("EHRbase upload OPT " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
		} catch (Exception e) {
			throw new RuntimeException("EHRbase upload OPT failed", e);
		}
	}

    /**
	 * Find an EHR linked to the given FHIR Patient id via EHR_STATUS.subject.external_ref.
	 */
	public String findEhrByPatient(String patientId) {
		String aql = "SELECT e/ehr_id/value FROM EHR e " +
				"WHERE e/ehr_status/subject/external_ref/id/value = '" + patientId + "' " +
				"AND e/ehr_status/subject/external_ref/namespace = '" + NAMESPACE + "'";
		try {
			ObjectNode body = objectMapper.createObjectNode();
			body.put("q", aql);
			HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body),
					headers(MediaType.APPLICATION_JSON));
			ResponseEntity<String> response = restTemplate.exchange(
					baseUrl + "/query/aql", HttpMethod.POST, request, String.class);
			JsonNode parsed = objectMapper.readTree(response.getBody());
			JsonNode rows = parsed.get("rows");
			if (rows != null && rows.isArray() && !rows.isEmpty()) {
				JsonNode first = rows.get(0);
				if (first.isArray() && !first.isEmpty()) {
					return first.get(0).asText();
				}
			}
		} catch (HttpClientErrorException e) {
			log.debug("findEhrByPatient query returned {}", e.getStatusCode());
		} catch (Exception e) {
			log.warn("findEhrByPatient failed: {}", e.getMessage());
		}
		return null;
	}

	/**
	 * Create a new EHR with subject linked to the given FHIR Patient id.
	 */
	public String createEhr(String patientId) {
		try {
			ObjectNode status = objectMapper.createObjectNode();
			status.put("_type", "EHR_STATUS");
			status.put("archetype_node_id", "openEHR-EHR-EHR_STATUS.generic.v1");
			status.putObject("name").put("value", "EHR Status");

			ObjectNode subject = status.putObject("subject");
			ObjectNode extRef = subject.putObject("external_ref");
			ObjectNode id = extRef.putObject("id");
			id.put("_type", "GENERIC_ID");
			id.put("value", patientId);
			id.put("scheme", "fhir");
			extRef.put("namespace", NAMESPACE);
			extRef.put("type", "PERSON");

			status.put("is_queryable", true);
			status.put("is_modifiable", true);

			HttpHeaders hdrs = headers(MediaType.APPLICATION_JSON);
			hdrs.set("Prefer", "return=representation");
			HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(status), hdrs);

			ResponseEntity<String> response = restTemplate.exchange(
					baseUrl + "/ehr", HttpMethod.POST, request, String.class);

			JsonNode parsed = objectMapper.readTree(response.getBody());
			JsonNode ehrIdNode = parsed.path("ehr_id").path("value");
			if (!ehrIdNode.isMissingNode()) {
				return ehrIdNode.asText();
			}
			// Fallback: check ETag or Location header
			String etag = response.getHeaders().getFirst("ETag");
			if (etag != null) return etag.replace("\"", "").trim();
			String location = response.getHeaders().getFirst("Location");
			if (location != null) {
				String[] parts = location.split("/");
				return parts[parts.length - 1].replace("\"", "").trim();
			}
			throw new RuntimeException("createEhr: no ehrId in response: " + response.getBody());
		} catch (HttpClientErrorException e) {
			throw new RuntimeException("EHRbase create EHR " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("EHRbase create EHR failed", e);
		}
	}

	/**
	 * Find an existing EHR for the patient, or create one if none exists.
	 */
	public String findOrCreateEhr(String patientId) {
		String ehrId = findEhrByPatient(patientId);
		if (ehrId != null) return ehrId;
		return createEhr(patientId);
	}

	/**
	 * POST a FLAT-format Composition to the given EHR. Returns the composition uid.
	 */
	public String postCompositionFlat(String ehrId, String flatBody, String templateId) {
		try {
			String tidParam = (templateId != null)
					? "&templateId=" + templateId
					: "";
			String url = baseUrl + "/ehr/" + ehrId + "/composition?format=FLAT" + tidParam;

			HttpHeaders hdrs = headers(MediaType.APPLICATION_JSON);
			hdrs.set("Prefer", "return=representation");
			HttpEntity<String> request = new HttpEntity<>(flatBody, hdrs);

			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
			String responseBody = response.getBody();

			// Try to extract compositionUid from response body
			JsonNode parsed = objectMapper.readTree(responseBody);
			if (parsed != null) {
				JsonNode ctxUid = parsed.path("ctx/composition_uid");
				if (!ctxUid.isMissingNode()) return ctxUid.asText();
				JsonNode compUid = parsed.path("compositionUid");
				if (!compUid.isMissingNode()) return compUid.asText();
				JsonNode uidValue = parsed.path("uid").path("value");
				if (!uidValue.isMissingNode()) return uidValue.asText();
			}
			// Fallback: ETag or Location
			String etag = response.getHeaders().getFirst("ETag");
			if (etag != null) return etag.replace("\"", "").trim();
			String location = response.getHeaders().getFirst("Location");
			if (location != null) {
				String[] parts = location.split("/");
				return parts[parts.length - 1].replace("\"", "").trim();
			}
			throw new RuntimeException("EHRbase POST composition: no uid in response: " + responseBody);
		} catch (HttpClientErrorException e) {
			throw new RuntimeException("EHRbase POST composition " + e.getStatusCode() + ": "
					+ e.getResponseBodyAsString(), e);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("EHRbase POST composition failed", e);
		}
	}

	public String getCompositionFlat(String ehrId, String compositionUid) {
		try {
			String url = baseUrl + "/ehr/" + ehrId + "/composition/" + compositionUid + "?format=FLAT";
			HttpEntity<Void> request = new HttpEntity<>(headers(MediaType.APPLICATION_JSON));
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
			return response.getBody();
		} catch (HttpClientErrorException e) {
			throw new RuntimeException("EHRbase GET composition " + e.getStatusCode() + ": "
					+ e.getResponseBodyAsString(), e);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("EHRbase GET composition failed", e);
		}
	}

	/**
	 * Execute an AQL query and return the parsed rows. Each row is a JsonNode array.
	 */
	public List<JsonNode> executeAql(String aql) {
		try {
			ObjectNode body = objectMapper.createObjectNode();
			body.put("q", aql);
			HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body),
					headers(MediaType.APPLICATION_JSON));
			ResponseEntity<String> response = restTemplate.exchange(
					baseUrl + "/query/aql", HttpMethod.POST, request, String.class);
			JsonNode parsed = objectMapper.readTree(response.getBody());
			JsonNode rows = parsed.get("rows");
			if (rows != null && rows.isArray()) {
				List<JsonNode> result = new ArrayList<>();
				for (JsonNode row : rows) {
					result.add(row);
				}
				return result;
			}
			return Collections.emptyList();
		} catch (HttpClientErrorException e) {
			throw new RuntimeException("EHRbase AQL " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("EHRbase AQL failed", e);
		}
	}
}
