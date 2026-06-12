/**
 * Author: Roeland Luykx
 * 
 * Copyright (c) 2026+ by RALY GmbH
 */

package ch.hl7.vacd.api.utils;

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

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Composition;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ch.hl7.vacd.api.domain.Peeled;
import ch.hl7.vacd.api.entity.ResourceIdentifier;

/**
 * 
 */
public class RessourceUtil {

	public static String removeUrn(String id) {
		if (id != null && id.startsWith("urn:uuid:")) {
			return id.substring(9);
		} else if (id != null && id.startsWith("urn:oid:")) {
			return id.substring(8);
		}
		return id;
	}

	public static Bundle ensureBundleProfile(Bundle bundle, String chVacdBundleProfile) {
		Meta meta = bundle.getMeta();
		if (meta != null) {
			List<String> profiles = meta.getProfile().stream().map(CanonicalType::getValue)
					.collect(Collectors.toList());
			if (profiles.contains(chVacdBundleProfile)) {
				return bundle;
			}
		}
		if (meta == null) {
			meta = new Meta();
			bundle.setMeta(meta);
		}
		meta.addProfile(chVacdBundleProfile);
		return bundle;
	}

	// --- ID extraction ---
	public static String extractId(Resource resource, Map<Resource, String> fullUrlMap) {
		String id = resource.getIdElement() != null ? resource.getIdElement().getIdPart() : null;
		if (id != null && !id.isEmpty()) {
			return removeUrn(id);
		}
		String fullUrl = fullUrlMap.get(resource);
		if (fullUrl != null && fullUrl.startsWith("urn:uuid:")) {
			return removeUrn(fullUrl);
		}
		return UUID.randomUUID().toString();
	}

	public static Peeled peel(Bundle bundle) {
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
			if (resource == null)
				continue;
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
			throw new UnprocessableEntityException("Composition.subject (" + subjectRef.getReference()
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

		return new Peeled(composition, immunizations, patient, practitioners, organizations, locations,
				practitionerRoles);
	}

	// --- Immunization status validation ---
	public static void validateStatus(Immunization immunization) {
		String status = immunization.getStatus() != null ? immunization.getStatus().toCode() : null;
		if (!"completed".equals(status)) {
			throw new UnprocessableEntityException(
					"Immunization.status must be 'completed' for ingestion (was '" + status + "')");
		}
	}

	// --- Full URL map building ---

	public static Map<Resource, String> buildFullUrlMap(Bundle bundle) {
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

	/**
	 * Splits the enriched FLAT JSON into separate documents per immunization. Keys
	 * containing "medication_management:X" are grouped by index X. Keys NOT
	 * containing "medication_management:" are common context and are included in
	 * every split document. Each split document renumbers its medication_management
	 * index to :0 so EHRbase sees a single-entry composition.
	 */
	public static List<String> splitByMedicationManagement(String enrichedFlatJson) {
		ObjectMapper OBJECT_MAPPER = new ObjectMapper();
		Pattern MED_MGMT_PATTERN = Pattern.compile("medication_management:(\\d+)");
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
					perImmunization.computeIfAbsent(index, k -> new LinkedHashMap<>()).put(key, entry.getValue());
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

	public static List<Identifier> getIdentifiers(Resource resource) {
		List<Identifier> identifiers = new ArrayList<>();
		if (resource instanceof Patient) {
			Patient patient = (Patient) resource;
			identifiers.addAll(patient.getIdentifier());
		} else if (resource instanceof Immunization) {
			Immunization immunization = (Immunization) resource;
			identifiers.addAll(immunization.getIdentifier());
		} else if (resource instanceof Practitioner) {
			Practitioner practitioner = (Practitioner) resource;
			identifiers.addAll(practitioner.getIdentifier());
		} else if (resource instanceof Organization) {
			Organization organization = (Organization) resource;
			identifiers.addAll(organization.getIdentifier());
		} else if (resource instanceof PractitionerRole) {
			PractitionerRole practitionerRole = (PractitionerRole) resource;
			identifiers.addAll(practitionerRole.getIdentifier());
		} else if (resource instanceof Composition) {
			Composition practitionerRole = (Composition) resource;
			identifiers.add(practitionerRole.getIdentifier());
		}
		return identifiers;
	}

}
