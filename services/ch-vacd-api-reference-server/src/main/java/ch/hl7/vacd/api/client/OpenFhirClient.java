package ch.hl7.vacd.api.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.hl7.vacd.api.provider.BundleProvider;
import net.sourceforge.plantuml.utils.Log;

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
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * Thin client for openFHIR 2.2.3.
 * Handles FHIR → openEHR FLAT JSON conversion via POST /openfhir/toopenehr.
 */
@Component
public class OpenFhirClient {

	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;
	private final String baseUrl;
	private final String defaultTemplateId;

    private static final Logger log = LoggerFactory.getLogger(BundleProvider.class);

	public OpenFhirClient(
			@Value("${openfhir.url:http://openfhir:8083}") String baseUrl,
			@Value("${openfhir.template-id:ch-vacd-immunization administration.v1-alpha}") String defaultTemplateId) {
		this.restTemplate = new RestTemplate();
		this.objectMapper = new ObjectMapper();
		// Strip trailing /openfhir if present
		this.baseUrl = baseUrl.replaceAll("/+$", "").replaceAll("/openfhir$", "").replaceAll("/+$", "");
		this.defaultTemplateId = defaultTemplateId;
	}

	/**
	 * Convert a FHIR Bundle JSON to openEHR FLAT format via openFHIR.
	 *
	 * @param fhirJson   the FHIR Bundle JSON string
	 * @param templateId optional template ID override (uses default if null)
	 * @return the FLAT JSON as a string (Jackson-parseable ObjectNode)
	 */
	public String toOpenEhr(String fhirJson, String templateId) {
		String tid = (templateId != null) ? templateId : defaultTemplateId;
		//String encodedTid = UriUtils.encode(tid, StandardCharsets.UTF_8);
		String url = baseUrl + "/openfhir/toopenehr?templateId=" + tid + "&flat=true";

		try {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.parseMediaType("application/fhir+json"));
			headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);

			HttpEntity<String> request = new HttpEntity<>(fhirJson, headers);
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

			String body = response.getBody();
			// Verify it's a JSON object
			JsonNode parsed = objectMapper.readTree(body);
			if (!parsed.isObject()) {
				throw new RuntimeException("openFHIR returned non-object: " + body);
			}
			return body;
		} catch (HttpClientErrorException e) {
			throw new RuntimeException("openFHIR toopenehr " + e.getStatusCode() + ": "
					+ e.getResponseBodyAsString(), e);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("openFHIR toopenehr failed", e);
		}
	}

	/**
	 * Convert a FHIR Bundle JSON to openEHR FLAT format using the default template ID.
	 */
	public String toOpenEhr(String fhirJson) {
		return toOpenEhr(fhirJson, defaultTemplateId);
	}

    /**
     * Converts a flattened openEHR JSON back to FHIR using openFHIR's /openfhir/tofhir endpoint.
     * @return the FHIR JSON as a string
     */
    public String toFhir(String flatJson) {
        return toFhir(flatJson, "ch-vacd-immunization administration.v1-alpha");
    }

    /**
     * Converts an openEHR JSON (canonical or flat) to FHIR using openFHIR's /openfhir/tofhir endpoint.
     * @param json       the openEHR JSON string
     * @param templateId the template ID to use for the conversion
     * @return the FHIR JSON as a string
     */
    public String toFhir(String json, String templateId) {
        String url = baseUrl + "/openfhir/tofhir?templateId=" + templateId;

        log.info(url);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);

            HttpEntity<String> request = new HttpEntity<>(json, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            return response.getBody();
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("openFHIR tofhir " + e.getStatusCode() + ": "
                    + e.getResponseBodyAsString(), e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("openFHIR tofhir failed", e);
        }
	}

	/**
	 * Check openFHIR health endpoint.
	 */
	public boolean isHealthy() {
		try {
			ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/health", String.class);
			return response.getStatusCode().is2xxSuccessful()
					&& response.getBody() != null
					&& response.getBody().contains("UP");
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * List OPTs registered in openFHIR.
	 */
	public JsonNode listOpts() {
		try {
			ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/opt", String.class);
			return objectMapper.readTree(response.getBody());
		} catch (Exception e) {
			return objectMapper.createArrayNode();
		}
	}

	/**
	 * Upload an OPT XML to openFHIR.
	 */
	public String postOpt(String xml) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_XML);
		HttpEntity<String> request = new HttpEntity<>(xml, headers);
		ResponseEntity<String> response = restTemplate.exchange(
				baseUrl + "/opt", HttpMethod.POST, request, String.class);
		return response.getBody();
	}

	/**
	 * List FHIRconnect contexts registered in openFHIR.
	 */
	public JsonNode listContexts() {
		try {
			ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/fc/context", String.class);
			return objectMapper.readTree(response.getBody());
		} catch (Exception e) {
			return objectMapper.createArrayNode();
		}
	}

	/**
	 * Upload a FHIRconnect context YAML to openFHIR.
	 */
	public String postContextYaml(String yaml) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("application/x-yaml"));
		HttpEntity<String> request = new HttpEntity<>(yaml, headers);
		ResponseEntity<String> response = restTemplate.exchange(
				baseUrl + "/fc/context", HttpMethod.POST, request, String.class);
		return response.getBody();
	}

	/**
	 * List FHIRconnect models registered in openFHIR.
	 */
	public JsonNode listModels() {
		try {
			ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/fc/model", String.class);
			return objectMapper.readTree(response.getBody());
		} catch (Exception e) {
			return objectMapper.createArrayNode();
		}
	}

	/**
	 * Upload a FHIRconnect model YAML to openFHIR.
	 */
	public String postModelYaml(String yaml) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("application/x-yaml"));
		HttpEntity<String> request = new HttpEntity<>(yaml, headers);
		ResponseEntity<String> response = restTemplate.exchange(
				baseUrl + "/fc/model", HttpMethod.POST, request, String.class);
		return response.getBody();
	}

	/**
	 * Delete a FHIRconnect context by name.
	 */
	public boolean deleteContext(String name) {
		try {
			restTemplate.delete(baseUrl + "/fc/context/" + name);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Delete a FHIRconnect model by name.
	 */
	public boolean deleteModel(String name) {
		try {
			restTemplate.delete(baseUrl + "/fc/model/" + name);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
