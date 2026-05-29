package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.client.FeederAuditEnricher;
import ch.hl7.vacd.api.client.OpenFhirClient;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.repo.ResourceRepository;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Tuple;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class BundleProvider implements IResourceProvider {

	private static final Logger log = LoggerFactory.getLogger(BundleProvider.class);

	private static final String CH_VACD_BUNDLE_PROFILE =
			"http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-document-immunization-administration";

	private final FhirContext fhirContext;
	private final ResourceRepository store;
	private final EhrbaseClient ehrbaseClient;
	private final OpenFhirClient openFhirClient;

	public BundleProvider(FhirContext fhirContext, ResourceRepository store,
			EhrbaseClient ehrbaseClient, OpenFhirClient openFhirClient) {
		this.fhirContext = fhirContext;
		this.store = store;
		this.ehrbaseClient = ehrbaseClient;
		this.openFhirClient = openFhirClient;
	}

	// --- Inner data class mirroring Kotlin's Peeled ---

	public static class Peeled {
		public final Composition composition;
		public final List<Immunization> immunizations;
		public final Patient patient;
		public final List<Practitioner> practitioners;
		public final List<Organization> organizations;
		public final List<Location> locations;
		public final List<PractitionerRole> practitionerRoles;

		public Peeled(Composition composition, List<Immunization> immunizations, Patient patient,
				List<Practitioner> practitioners, List<Organization> organizations,
				List<Location> locations, List<PractitionerRole> practitionerRoles) {
			this.composition = composition;
			this.immunizations = immunizations;
			this.patient = patient;
			this.practitioners = practitioners;
			this.organizations = organizations;
			this.locations = locations;
			this.practitionerRoles = practitionerRoles;
		}
	}

	// --- Bundle profile enforcement ---

	private Bundle ensureBundleProfile(Bundle bundle) {
		Meta meta = bundle.getMeta();
		if (meta != null) {
			List<String> profiles = meta.getProfile().stream()
					.map(CanonicalType::getValue)
					.collect(Collectors.toList());
			if (profiles.contains(CH_VACD_BUNDLE_PROFILE)) {
				return bundle;
			}
		}
		if (meta == null) {
			meta = new Meta();
			bundle.setMeta(meta);
		}
		meta.addProfile(CH_VACD_BUNDLE_PROFILE);
		return bundle;
	}

	// --- Bundle extraction (peel) ---

	private Peeled peel(Bundle bundle) {
		if (bundle.getType() != Bundle.BundleType.DOCUMENT) {
			throw new UnprocessableEntityException(
					"Bundle.type must be 'document' for a CH VACD Immunization Administration Document; got type="
							+ bundle.getType());
		}

		List<Bundle.BundleEntryComponent> entries = bundle.getEntry();
		if (entries == null || entries.isEmpty()) {
			throw new UnprocessableEntityException("Bundle has no entries");
		}

		// Index entries by Resource/id and by fullUrl for cross-reference resolution.
		Map<String, Resource> byRef = new HashMap<>();
		for (Bundle.BundleEntryComponent entry : entries) {
			Resource resource = entry.getResource();
			if (resource == null) continue;
			String rtype = resource.fhirType();
			String rid = resource.getIdElement() != null ? resource.getIdElement().getIdPart() : null;
			if (rid != null) {
				byRef.put(rtype + "/" + rid, resource);
			}
			String fullUrl = entry.getFullUrl();
			if (fullUrl != null && !fullUrl.isEmpty()) {
				byRef.put(fullUrl, resource);
			}
		}

		// entry[0] MUST be Composition.
		Resource first = entries.get(0).getResource();
		if (!(first instanceof Composition)) {
			throw new UnprocessableEntityException(
					"Bundle.entry[0] must be a Composition (CH VACD document profile); got "
							+ (first != null ? first.fhirType() : "null"));
		}
		Composition composition = (Composition) first;

		// Walk Composition.section[*].entry[*].reference to find Immunizations.
		List<Composition.SectionComponent> sections = composition.getSection();
		if (sections == null || sections.isEmpty()) {
			throw new UnprocessableEntityException("Composition has no section");
		}

		List<String> sectionRefs = new ArrayList<>();
		for (Composition.SectionComponent section : sections) {
			for (Reference ref : section.getEntry()) {
				if (ref.getReference() != null) {
					sectionRefs.add(ref.getReference());
				}
			}
		}

		List<Immunization> immunizations = new ArrayList<>();
		for (String ref : sectionRefs) {
			Resource resolved = byRef.get(ref);
			if (resolved instanceof Immunization) {
				immunizations.add((Immunization) resolved);
			}
		}
		if (immunizations.isEmpty()) {
			throw new UnprocessableEntityException(
					"Composition does not reference any Immunization in its sections; saw section references: "
							+ sectionRefs);
		}

		// subject MUST point at a Patient in the Bundle.
		Reference subjectRef = composition.getSubject();
		if (subjectRef == null || subjectRef.getReference() == null) {
			throw new UnprocessableEntityException("Composition.subject.reference missing");
		}
		Resource subjectResource = byRef.get(subjectRef.getReference());
		if (!(subjectResource instanceof Patient)) {
			throw new UnprocessableEntityException(
					"Composition.subject (" + subjectRef.getReference()
							+ ") does not resolve to a Patient in the Bundle");
		}
		Patient patient = (Patient) subjectResource;

		// Collect supporting resources by type.
		List<Practitioner> practitioners = new ArrayList<>();
		List<Organization> organizations = new ArrayList<>();
		List<Location> locations = new ArrayList<>();
		List<PractitionerRole> practitionerRoles = new ArrayList<>();
		for (Bundle.BundleEntryComponent entry : entries) {
			Resource resource = entry.getResource();
			if (resource instanceof Practitioner) {
				practitioners.add((Practitioner) resource);
			} else if (resource instanceof Organization) {
				organizations.add((Organization) resource);
			} else if (resource instanceof Location) {
				locations.add((Location) resource);
			} else if (resource instanceof PractitionerRole) {
				practitionerRoles.add((PractitionerRole) resource);
			}
		}

		return new Peeled(composition, immunizations, patient, practitioners,
				organizations, locations, practitionerRoles);
	}

	// --- Immunization status validation ---

	private void validateStatus(Immunization immunization) {
		String status = immunization.getStatus() != null ? immunization.getStatus().toCode() : null;
		if (!"completed".equals(status)) {
			throw new UnprocessableEntityException(
					"Immunization.status must be 'completed' for ingestion (was '" + status + "')");
		}
	}

	// --- Full URL map building ---

	private Map<Resource, String> buildFullUrlMap(Bundle bundle) {
		Map<Resource, String> map = new HashMap<>();
		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			Resource resource = entry.getResource();
			String fullUrl = entry.getFullUrl();
			if (resource != null && fullUrl != null && !fullUrl.isEmpty()) {
				map.put(resource, fullUrl);
			}
		}
		return map;
	}

	// --- ID extraction ---

	private String extractId(Resource resource, Map<Resource, String> fullUrlMap) {
		String id = resource.getIdElement() != null ? resource.getIdElement().getIdPart() : null;
		if (id != null && !id.isEmpty()) {
			return id;
		}
		String fullUrl = fullUrlMap.get(resource);
		if (fullUrl != null && fullUrl.startsWith("urn:uuid:")) {
			return fullUrl.substring("urn:uuid:".length());
		}
		return UUID.randomUUID().toString();
	}

	// --- Split enriched FLAT JSON by medication_management:X ---

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final Pattern MED_MGMT_PATTERN = Pattern.compile("medication_management:(\\d+)");

	/**
	 * Splits the enriched FLAT JSON into separate documents per immunization.
	 * Keys containing "medication_management:X" are grouped by index X.
	 * Keys NOT containing "medication_management:" are common context and are
	 * included in every split document. Each split document renumbers its
	 * medication_management index to :0 so EHRbase sees a single-entry composition.
	 */
	private List<String> splitByMedicationManagement(String enrichedFlatJson) {
		try {
			ObjectNode flat = (ObjectNode) OBJECT_MAPPER.readTree(enrichedFlatJson);

			// Separate common fields from medication_management-specific fields.
			Map<String, JsonNode> commonFields = new LinkedHashMap<>();
			Map<Integer, Map<String, JsonNode>> perImmunization = new LinkedHashMap<>();

			Set<Map.Entry<String, JsonNode>> fields = flat.properties();
			for (Map.Entry<String, JsonNode> entry : fields) {
				String key = entry.getKey();
				Matcher matcher = MED_MGMT_PATTERN.matcher(key);
				if (matcher.find()) {
					int index = Integer.parseInt(matcher.group(1));
					perImmunization.computeIfAbsent(index, k -> new LinkedHashMap<>())
							.put(key, entry.getValue());
				} else {
					commonFields.put(key, entry.getValue());
				}
			}

			// If no medication_management keys found, return the whole document as-is.
			if (perImmunization.isEmpty()) {
				return List.of(enrichedFlatJson);
			}

			// Build one complete document per immunization index.
			List<String> result = new ArrayList<>();
			for (Map.Entry<Integer, Map<String, JsonNode>> immEntry : perImmunization.entrySet()) {
				int originalIndex = immEntry.getKey();
				Map<String, JsonNode> immFields = immEntry.getValue();

				ObjectNode doc = OBJECT_MAPPER.createObjectNode();

				// Add common (preceding/following) fields.
				for (Map.Entry<String, JsonNode> common : commonFields.entrySet()) {
					doc.set(common.getKey(), common.getValue());
				}

				// Add immunization-specific fields, renumbered to :0.
				String indexStr = "medication_management:" + originalIndex;
				String targetStr = "medication_management:0";
				for (Map.Entry<String, JsonNode> immField : immFields.entrySet()) {
					String renumberedKey = immField.getKey().replace(indexStr, targetStr);
					doc.set(renumberedKey, immField.getValue());
				}

				result.add(OBJECT_MAPPER.writeValueAsString(doc));
			}

			return result;
		} catch (Exception e) {
			throw new RuntimeException("Failed to split enriched FLAT JSON by medication_management", e);
		}
	}

	// --- CRUD operations ---

	@Create
	public MethodOutcome create(@ResourceParam Bundle bundle) {
		// Ensure CH VACD profile is present in meta.
		bundle = ensureBundleProfile(bundle);

		// Validate and extract bundle structure.
		Peeled peeled = peel(bundle);

		// Validate each Immunization status.
		for (Immunization immunization : peeled.immunizations) {
			validateStatus(immunization);
		}

		// Build fullUrl map and extract IDs.
		Map<Resource, String> fullUrlMap = buildFullUrlMap(bundle);
		String patientId = extractId(peeled.patient, fullUrlMap);
		patientId = patientId.substring("urn:uuid:".length());
		List<String> practitionerIds = peeled.practitioners.stream()
				.map(p -> extractId(p, fullUrlMap))
				.collect(Collectors.toList());
		List<String> organizationIds = peeled.organizations.stream()
				.map(o -> extractId(o, fullUrlMap))
				.collect(Collectors.toList());

		String ehrId = ehrbaseClient.findEhrByPatient(patientId);
		log.info("Found ehrId: {} for patientId: {}", ehrId, patientId);

		// Assign Bundle ID and persist to the FHIR store.
		String type = bundle.fhirType();
		String id = bundle.getIdElement() != null && bundle.getIdElement().hasIdPart()
				? bundle.getIdElement().getIdPart()
				: UUID.randomUUID().toString();
		bundle.setId(type + "/" + id);

		for (Immunization immunization : peeled.immunizations) {
			var identifiers = immunization.getIdentifier();
			identifiers.add(
				new Identifier()
					.setSystem("urn:che:epr:ch-vacd:ehr-id")
					.setValue("urn:uuid:" + ehrId)
			);
			immunization.setIdentifier(identifiers);
		}
		String bundleJson = fhirContext.newJsonParser().encodeResourceToString(bundle);

		log.info("FHIR server accepted Bundle, id={}", id);

		// Convert FHIR Bundle to openEHR FLAT format via openFHIR.
		String flatJson = openFhirClient.toOpenEhr(bundleJson);
		log.info(flatJson);

		// Enrich with feeder_audit (Konkretisierung §13) and composition metadata.
		String enrichedFlat = FeederAuditEnricher.addOriginal(flatJson, bundleJson);

		// Split the enriched FLAT JSON by medication_management:X identifier.
		// Each immunization gets its own complete document with common fields.
		List<String> splitDocuments = splitByMedicationManagement(enrichedFlat);

		// Persist each split immunization document separately.
		List<String> compositionUids = new ArrayList<>();
		log.info("Storing {} split Composition documents for Bundle.id={} patientId={} practitioners={} organizations={}",
				splitDocuments.size(), id, patientId, practitionerIds, organizationIds);
		for (int i = 0; i < splitDocuments.size(); i++) {
			String splitDoc = splitDocuments.get(i);

			log.info("Split document for immunization index {}:\n{}", i, splitDoc);

			String compositionUid = ehrbaseClient.postCompositionFlat(ehrId, splitDoc, "ch-vacd-immunization administration.v1-alpha");
			compositionUids.add(compositionUid);
			log.info("Stored split Composition[{}] uid={} ehrId={}", i, compositionUid, ehrId);

			// Store the Immunization FHIR resource with compositionUid identifier for later retrieval.
			if (i < peeled.immunizations.size()) {
				Immunization imm = peeled.immunizations.get(i);
				imm.addIdentifier(new Identifier()
						.setSystem("urn:che:epr:ch-vacd:composition-uid")
						.setValue(compositionUid));
				String immJson = fhirContext.newJsonParser().encodeResourceToString(imm);
				String immId = extractId(imm, fullUrlMap);

				ResourceEntity immEntity = new ResourceEntity();
				immEntity.setResourceType("Immunization");
				immEntity.setResourceId(immId);
				immEntity.setJson(immJson);
				store.save(immEntity);
				log.info("Stored Immunization id={} with compositionUid={}", immId, compositionUid);
			}
		}

		log.info("Completed ingestion: bundleId={} patientId={} practitioners={} organizations={} immunizations={} compositions={}",
				id, patientId, practitionerIds, organizationIds, peeled.immunizations.size(), compositionUids.size());

		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType("Bundle", id));
		outcome.setResource(bundle);
		return outcome;
	}

	@Read
	public Bundle read(@IdParam IdType id) {
		List<ResourceEntity> found = store.findByResourceTypeAndResourceId("Bundle", id.getIdPart());
		if (found != null && !found.isEmpty()) {
			IBaseResource r = fhirContext.newJsonParser().parseResource(found.get(0).getJson());
			if (r instanceof Bundle) return (Bundle) r;
		}
		return null;
	}

	@Search
	public List<Bundle> search() {
		List<ResourceEntity> entities = store.findByResourceType("Bundle");
		List<Bundle> out = new ArrayList<>();
		for (ResourceEntity e : entities) {
			IBaseResource r = fhirContext.newJsonParser().parseResource(e.getJson());
			if (r instanceof Bundle) out.add((Bundle) r);
		}
		return out;
	}

	@Override
	public Class<Bundle> getResourceType() {
		return Bundle.class;
	}
}
