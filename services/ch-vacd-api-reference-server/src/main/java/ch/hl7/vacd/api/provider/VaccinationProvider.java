package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.client.OpenFhirClient;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.repo.ResourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.apache.catalina.loader.ResourceEntry;
import org.hl7.fhir.instance.model.api.IBaseResource;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Read-path provider: builds a CH VACD Vaccination Record FHIR Bundle for
 * one patient EHR.
 *
 * <ol>
 *   <li>ONE AQL on EHRbase → list of canonical admin compositions</li>
 *   <li>Merge content[] arrays → one canonical composition (in-memory)</li>
 *   <li>ONE POST to openFHIR /tofhir → ch-vacd-document-vaccination-record Bundle</li>
 * </ol>
 *
 * Invoke: GET /Bundle/$vaccination-record?ehrId={ehrId}
 */
@Component
public class VaccinationProvider {

	private final AllergyIntoleranceProvider allergyIntoleranceProvider;

    private static final Logger log = LoggerFactory.getLogger(VaccinationProvider.class);

	private static final String ADMIN_TEMPLATE =
			"ch-vacd-immunization administration.v1-alpha";
	private static final String VACC_TEMPLATE =
			"ch-vacd-vaccination-record.v1-alpha";

	private final FhirContext fhirContext;
	private final EhrbaseClient ehrbaseClient;
	private final OpenFhirClient openFhirClient;
	private final ResourceRepository store;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public VaccinationProvider(FhirContext fhirContext, EhrbaseClient ehrbaseClient,
			OpenFhirClient openFhirClient, ResourceRepository store, AllergyIntoleranceProvider allergyIntoleranceProvider) {
		this.fhirContext = fhirContext;
		this.ehrbaseClient = ehrbaseClient;
		this.openFhirClient = openFhirClient;
		this.store = store;
        this.allergyIntoleranceProvider = allergyIntoleranceProvider;
	}

	/**
	 * Build a vaccination record by EHR identifier.
	 * GET /Bundle/$vaccination-record?ehrId={ehrId}
	 */
	@Operation(name = "$vaccination-record", type = Bundle.class)
	public Bundle vaccinationRecord(
			@OperationParam(name = "patientId", min = 1) StringType patientIdParam) {

		String patientId = patientIdParam.getValue();
		if (patientId == null || patientId.isBlank()) {
			throw new InvalidRequestException("patientId parameter is required");
		}

        String ehrId = ehrbaseClient.findEhrByPatient(patientId);

		log.info("Building vaccination record for patientId={} (ehrId={})", patientId, ehrId);

		try {
			// Step 1: Fetch all admin compositions from EHRbase via AQL.
			List<JsonNode> compositions = fetchAdminCompositions(ehrId);
			if (compositions.isEmpty()) {
				log.info("No admin compositions found for ehrId={}", ehrId);
				return null;
			}
			log.info("Found {} admin composition(s) for ehrId={}", compositions.size(), ehrId);

			// Step 2: Merge content[] arrays across all compositions.
			ObjectNode merged = mergeCompositions(compositions);

			// Step 3: Convert merged canonical composition to FHIR via openFHIR.
			String mergedJson = objectMapper.writeValueAsString(merged);
			String fhirJson = openFhirClient.toFhir(mergedJson, VACC_TEMPLATE);
            log.info("Converted FHIR JSON:\n{}", fhirJson);

			Bundle bundle = (Bundle) fhirContext.newJsonParser().parseResource(fhirJson);
            bundle.setId("urn:uuid:" + UUID.randomUUID().toString());
            var immEntries = bundle.getEntry().stream()
                    .filter(e -> e.getResource() instanceof Immunization)
                    .map(e -> (Immunization) e.getResource())
                    .collect(Collectors.toList());

            log.info("Bundle contains {} Immunization entries", immEntries.size());
            final String practitionerRoleId = immEntries.stream()
                    .filter(imm -> imm.getPerformer() != null && !imm.getPerformer().isEmpty())
                    .map(imm -> imm.getPerformer().get(0).getActor().getIdentifier() != null ? imm.getPerformer().get(0).getActor().getIdentifier().getValue().substring("urn:uuid:".length()) : null)
                    .findFirst()
                    .orElse(null);

			// Step 4: Enrich the Bundle with Patient, Practitioner, PractitionerRole, Organization from the store.
            log.info("Before enrich Bundle content:\n{}", fhirContext.newJsonParser().encodeResourceToString(bundle));

			enrichBundleWithResources(bundle, patientId, practitionerRoleId);

            immEntries.get(0).setPatient(new Reference().setReference("urn:uuid:" + patientId));
            Composition comp = bundle.getEntry().stream()
                    .filter(e -> e.getResource() instanceof Composition)
                    .map(e -> (Composition) e.getResource())
                    .findFirst()
                    .orElse(null);

            if (comp != null) {
                comp.setSubject(new Reference().setReference("urn:uuid:" + patientId));
                comp.setDate(new Date());
                comp.setAuthor(List.of(new Reference().setReference("urn:uuid:" + practitionerRoleId)));
            }

			log.info("Built vaccination record Bundle with {} entries for ehrId={}",
					bundle.getEntry().size(), ehrId);

            log.info("Bundle content:\n{}", fhirContext.newJsonParser().encodeResourceToString(bundle));

			return bundle;
		} catch (Exception e) {
			log.error("Failed to build vaccination record for ehrId={}: {}", ehrId, e.getMessage(), e);
			throw new InvalidRequestException("Failed to build vaccination record: " + e.getMessage());
		}
	}

	/**
	 * Step 1: ONE AQL query returns N rows of [canonical COMPOSITION].
	 */
	private List<JsonNode> fetchAdminCompositions(String ehrId) {
		String aql = "SELECT c " +
				"FROM EHR e CONTAINS COMPOSITION c " +
				"WHERE c/archetype_details/template_id/value = '" + ADMIN_TEMPLATE + "' " +
				"AND e/ehr_id/value = '" + ehrId + "'";

		List<JsonNode> rows = ehrbaseClient.executeAql(aql);

		// Each row is a single-column array [composition]; extract row[0].
		return rows.stream()
				.map(row -> row.get(0))
				.collect(Collectors.toList());
	}

	/**
	 * Step 2: Deep-clone the first composition as a wrapper, replace its
	 * content[] with every ACTION from every source composition, sorted
	 * chronologically by ACTION.time.
	 */
	private ObjectNode mergeCompositions(List<JsonNode> compositions) {
		ObjectNode merged = compositions.get(0).deepCopy();

		ArrayNode allActions = compositions.stream()
				.flatMap(c -> StreamSupport.stream(c.path("content").spliterator(), false))
				.sorted(Comparator.comparing(a -> a.path("time").path("value").asText("")))
				.collect(objectMapper::createArrayNode, ArrayNode::add, ArrayNode::addAll);

		merged.set("content", allActions);
		return merged;
	}

	/**
	 * Step 4: Fetch Patient, Practitioner, PractitionerRole, and Organization
	 * from the FHIR store and add them as entries to the Bundle.
	 */
	private void enrichBundleWithResources(Bundle bundle, String patientId, String practitionerRoleId) {
		// Fetch Patient
		addResourceEntries(bundle, "Patient", patientId);

		// Fetch all Practitioners, PractitionerRoles, Organizations from the store
		addResourceEntries(bundle, "PractitionerRole", practitionerRoleId);

        PractitionerRole practitionerRole = getResourceEntry("PractitionerRole", practitionerRoleId);
        if (practitionerRole == null) {
            log.warn("PractitionerRole with id={} not found in store, skipping Practitioner and Organization enrichment", practitionerRoleId);
            return;
        }
        String practitionerId = practitionerRole.getPractitioner().getReference().substring("urn:uuid:".length());
        addResourceEntries(bundle, "Practitioner", practitionerId);
        String organizationId = practitionerRole.getOrganization().getReference().substring("urn:uuid:".length());
        addResourceEntries(bundle, "Organization", organizationId);
	}

    private <T extends Resource> T getResourceEntry(String resourceType, String resourceId) {
        List<ResourceEntity> entities = store.findByResourceTypeAndResourceId(resourceType, resourceId);
        if (entities != null && !entities.isEmpty()) {
            ResourceEntity entity = entities.get(0);
            try {
                return (T) fhirContext.newJsonParser().parseResource(entity.getJson());
            } catch (Exception e) {
                log.warn("Failed to parse stored {} id={}: {}", resourceType, entity.getResourceId(), e.getMessage());
            }
        }
        return null;
    }

	private void addResourceEntries(Bundle bundle, String resourceType, String resourceId) {
		List<ResourceEntity> entities = store.findByResourceTypeAndResourceId(resourceType, resourceId);
		for (ResourceEntity entity : entities) {
			try {
				IBaseResource resource = fhirContext.newJsonParser().parseResource(entity.getJson());
				if (resource instanceof Resource) {
					Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
					entry.setFullUrl("urn:uuid:" + (entity.getResourceId() != null ? entity.getResourceId() : UUID.randomUUID().toString()));
					entry.setResource((Resource) resource);
					bundle.addEntry(entry);
				}
			} catch (Exception e) {
				log.warn("Failed to parse stored {} id={}: {}", resourceType, entity.getResourceId(), e.getMessage());
			}
		}
	}
}
