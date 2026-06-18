package ch.hl7.vacd.api.business.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.client.FeederAuditEnricher;
import ch.hl7.vacd.api.client.OpenFhirClient;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.entity.ResourceIdentifierEntity;
import ch.hl7.vacd.api.entity.ResourceReferenceEntity;
import ch.hl7.vacd.api.openehr.ChVacdOpenEhrConstants;
import ch.hl7.vacd.api.repo.ResourceRepository;
import ch.hl7.vacd.api.utils.RessourceUtil;

public class AbstractBusinessService {

	private static final Logger log = LoggerFactory.getLogger(AbstractBusinessService.class);

	protected final FhirContext fhirContext;
	protected final ResourceRepository store;
	protected final EhrbaseClient ehrbaseClient;
	protected final OpenFhirClient openFhirClient;

	public AbstractBusinessService(FhirContext fhirContext, ResourceRepository store, OpenFhirClient openFhirClient,
			EhrbaseClient ehrbaseClient) {
		this.fhirContext = fhirContext;
		this.store = store;
		this.openFhirClient = openFhirClient;
		this.ehrbaseClient = ehrbaseClient;

	}

	// --- CreateIfAbsent ---
	protected ResourceEntity createIfAbsent(Resource resource, Map<Resource, String> fullUrlMap) {
		final ResourceEntity entity = new ResourceEntity();
		String resourceType = resource.fhirType();
		String resourceId = RessourceUtil.extractId(resource, fullUrlMap);
		resource.setId(resourceId);
		List<ResourceEntity> existing = store.findByResourceTypeAndResourceId(resourceType,
				RessourceUtil.removeUrn(resourceId));
		if (existing == null || existing.isEmpty()) {
			entity.setResourceType(resourceType);
			entity.setResourceId(RessourceUtil.removeUrn(resourceId));
			entity.setJson(fhirContext.newJsonParser().encodeResourceToString(resource));

			RessourceUtil.getIdentifiers(resource).forEach(identifier -> {
				entity.addIdentifier(new ResourceIdentifierEntity()//
						.setIdSystem(identifier.getSystem())//
						.setIdValue(identifier.getValue())//
						.setIdUse((identifier.getUse() != null) ? identifier.getUse().toCode() : null)//
						.setResourceEntity(entity));
			});

			store.save(entity);
			log.info("Created absent {} id={}", resourceType, resourceId);
		}
		return entity;
	}

	protected void checkForReference(String resourceType, String idPart) {
		List<ResourceEntity> existing = store.findByResourceTypeAndResourceId(resourceType, idPart);
		if (existing == null || existing.isEmpty()) {
			throw new ResourceNotFoundException(
					String.format("Referenced resource %s/%s not found", resourceType, idPart));
		}
	}

	protected Bundle processImmunizationAdmnistration(Bundle bundle, Map<Resource, String> fullUrlMap,
			List<String> compositionUids2, List<Immunization> immunizations, String ehrId, String patientId) {
		String bundleJson = fhirContext.newJsonParser().encodeResourceToString(bundle);

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
		
		for (int i = 0; i < splitDocuments.size(); i++) {
			String splitDoc = splitDocuments.get(i);

			log.info("Split document for immunization index {}:\n{}", i, splitDoc);

			String compositionUid = ehrbaseClient.postCompositionFlat(ehrId, splitDoc,
					ChVacdOpenEhrConstants.ADMIN_TEMPLATE);
			compositionUids.add(compositionUid);
			log.info("Stored split Composition[{}] uid={} ehrId={}", i, compositionUid, ehrId);

			// Store the Immunization FHIR resource with compositionUid identifier for later
			// retrieval.
			if (i < immunizations.size()) {
				Immunization imm = immunizations.get(i);
				imm.addIdentifier(
						new Identifier().setSystem("urn:che:epr:ch-vacd:composition-uid").setValue(compositionUid));

				
				ResourceEntity entity = createIfAbsent(imm, fullUrlMap);
				try {
					ResourceReferenceEntity refEntity = new ResourceReferenceEntity()//
							.setTargetType("Patient")//
							.setTargetId(patientId)//
							.setSourceEntity(entity)//
							.setSourceField("Immunization.patient");
	
					entity.addReference(refEntity);
					
					
					
	
					store.save(entity);
				} catch (Exception e) {
					log.error("Error saving Immunization resource to local store: {}", e.getMessage(), e);
				}

				String immId = RessourceUtil.extractId(imm, fullUrlMap);
				log.info("Stored Immunization id={} with compositionUid={}", immId, compositionUid);
			}
		}

		return bundle;

	}
	
	protected <T extends Resource> T getResourceEntry(String resourceType, String resourceId) {
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

}
