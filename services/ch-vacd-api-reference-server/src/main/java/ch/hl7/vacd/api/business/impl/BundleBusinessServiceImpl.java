/**
 * Author: Roeland Luykx
 * 
 * Copyright (c) 2026+ by RALY GmbH
 */

package ch.hl7.vacd.api.business.impl;

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

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.projecthusky.fhir.vacd.ch.common.enums.ChVacdDocumentType;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunizationAdministrationDocument;
import org.projecthusky.fhir.vacd.ch.common.service.ChVacdParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ch.hl7.vacd.api.business.BundleBusinessService;
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.client.FeederAuditEnricher;
import ch.hl7.vacd.api.client.OpenFhirClient;
import ch.hl7.vacd.api.domain.Peeled;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.provider.BundleProvider;
import ch.hl7.vacd.api.repo.ResourceRepository;
import ch.hl7.vacd.api.utils.RessourceUtil;

/**
 * 
 */
@Service
public class BundleBusinessServiceImpl implements BundleBusinessService {

	private static final Logger log = LoggerFactory.getLogger(BundleProvider.class);

	private final FhirContext fhirContext;
	private final ResourceRepository store;
	private final EhrbaseClient ehrbaseClient;
	private final OpenFhirClient openFhirClient;

	public BundleBusinessServiceImpl(FhirContext fhirContext, ResourceRepository store, EhrbaseClient ehrbaseClient,
			OpenFhirClient openFhirClient) {
		this.fhirContext = fhirContext;
		this.store = store;
		this.ehrbaseClient = ehrbaseClient;
		this.openFhirClient = openFhirClient;
	}
		

	// --- CreateIfAbsent ---
	private void createIfAbsent(Resource resource, Map<Resource, String> fullUrlMap) {
		String resourceType = resource.fhirType();
		String resourceId = RessourceUtil.extractId(resource, fullUrlMap);
		List<ResourceEntity> existing = store.findByResourceTypeAndResourceId(resourceType,
				RessourceUtil.removeUrn(resourceId));
		if (existing == null || existing.isEmpty()) {
			ResourceEntity entity = new ResourceEntity();
			entity.setResourceType(resourceType);
			entity.setResourceId(RessourceUtil.removeUrn(resourceId));
			entity.setJson(fhirContext.newJsonParser().encodeResourceToString(resource));
			store.save(entity);
			log.info("Created absent {} id={}", resourceType, resourceId);
		}
	}
	

	@Override
	public Bundle createBundle(Bundle bundle) {

		try {
			ChVacdParser parser = new ChVacdParser(fhirContext);
			ChVacdImmunizationAdministrationDocument ref = parser
					.parse(fhirContext.newJsonParser().encodeToString(bundle), ChVacdDocumentType.ADMIN);

			ref.getId();
		} catch (Exception e) {
			log.error("Failed to parse incoming Bundle as ChVacdImmunizationAdministrationDocument", e);
		}

		// Validate and extract bundle structure.
		Peeled peeled = RessourceUtil.peel(bundle);

		// Validate each Immunization status.
		for (Immunization immunization : peeled.immunizations) {
			RessourceUtil.validateStatus(immunization);
		}

		// CreateIfAbsent for Practitioners, Organizations, and PractitionerRoles.
		Map<Resource, String> fullUrlMap = RessourceUtil.buildFullUrlMap(bundle);
		for (Practitioner practitioner : peeled.practitioners) {
			createIfAbsent(practitioner, fullUrlMap);
		}
		for (Organization organization : peeled.organizations) {
			createIfAbsent(organization, fullUrlMap);
		}
		for (PractitionerRole practitionerRole : peeled.practitionerRoles) {
			createIfAbsent(practitionerRole, fullUrlMap);
		}

		// Extract IDs.
		String patientId = RessourceUtil.extractId(peeled.patient, fullUrlMap);
		patientId = patientId.substring("urn:uuid:".length());
		List<String> practitionerIds = peeled.practitioners.stream().map(p -> RessourceUtil.extractId(p, fullUrlMap))
				.collect(Collectors.toList());
		List<String> organizationIds = peeled.organizations.stream().map(o -> RessourceUtil.extractId(o, fullUrlMap))
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
//			var identifiers = immunization.getIdentifier();
//			identifiers.add(
//				new Identifier()
//					.setSystem("urn:che:epr:ch-vacd:ehr-id")
//					.setValue("urn:uuid:" + ehrId)
//			);
//			immunization.setIdentifier(identifiers);
			immunization.addIdentifier(
					new Identifier().setSystem("urn:che:epr:ch-vacd:ehr-id").setValue("urn:uuid:" + ehrId));
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
		List<String> splitDocuments = RessourceUtil.splitByMedicationManagement(enrichedFlat);

		// Persist each split immunization document separately.
		List<String> compositionUids = new ArrayList<>();
		log.info(
				"Storing {} split Composition documents for Bundle.id={} patientId={} practitioners={} organizations={}",
				splitDocuments.size(), id, patientId, practitionerIds, organizationIds);
		for (int i = 0; i < splitDocuments.size(); i++) {
			String splitDoc = splitDocuments.get(i);

			log.info("Split document for immunization index {}:\n{}", i, splitDoc);

			String compositionUid = ehrbaseClient.postCompositionFlat(ehrId, splitDoc,
					"ch-vacd-immunization administration.v1-alpha");
			compositionUids.add(compositionUid);
			log.info("Stored split Composition[{}] uid={} ehrId={}", i, compositionUid, ehrId);

			// Store the Immunization FHIR resource with compositionUid identifier for later
			// retrieval.
			if (i < peeled.immunizations.size()) {
				Immunization imm = peeled.immunizations.get(i);
				imm.addIdentifier(
						new Identifier().setSystem("urn:che:epr:ch-vacd:composition-uid").setValue(compositionUid));

				createIfAbsent(imm, fullUrlMap);

//				String immJson = fhirContext.newJsonParser().encodeResourceToString(imm);
				String immId = RessourceUtil.extractId(imm, fullUrlMap);
//				
//				List<ResourceEntity> existing = store.findByResourceTypeAndResourceId(imm.getResourceType(), RessourceUtil.removeUrn( immId));
//
//				ResourceEntity immEntity = new ResourceEntity();
//				immEntity.setResourceType("Immunization");
//				immEntity.setResourceId(RessourceUtil.removeUrn(immId));
//				immEntity.setJson(immJson);
//				store.save(immEntity);
				log.info("Stored Immunization id={} with compositionUid={}", immId, compositionUid);
			}
		}

		log.info(
				"Completed ingestion: bundleId={} patientId={} practitioners={} organizations={} immunizations={} compositions={}",
				id, patientId, practitionerIds, organizationIds, peeled.immunizations.size(), compositionUids.size());

		return bundle;

	}

	@Override
	public Bundle readBundle(IdType id) {
		List<ResourceEntity> found = store.findByResourceTypeAndResourceId("Bundle", id.getIdPart());
		if (found != null && !found.isEmpty()) {
			IBaseResource r = fhirContext.newJsonParser().parseResource(found.get(0).getJson());
			if (r instanceof Bundle)
				return (Bundle) r;
		}
		return null;
	}

	@Override
	public List<Bundle> searchBundles() {
		List<ResourceEntity> entities = store.findByResourceType("Bundle");
		List<Bundle> out = new ArrayList<>();
		for (ResourceEntity e : entities) {
			IBaseResource r = fhirContext.newJsonParser().parseResource(e.getJson());
			if (r instanceof Bundle)
				out.add((Bundle) r);
		}
		return out;
	}

}
