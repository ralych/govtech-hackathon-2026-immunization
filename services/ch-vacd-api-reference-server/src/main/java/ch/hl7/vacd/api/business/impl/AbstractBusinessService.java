package ch.hl7.vacd.api.business.impl;

import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.entity.ResourceIdentifier;
import ch.hl7.vacd.api.repo.ResourceRepository;
import ch.hl7.vacd.api.utils.RessourceUtil;

public class AbstractBusinessService {

	private static final Logger log = LoggerFactory.getLogger(AbstractBusinessService.class);

	protected final FhirContext fhirContext;
	protected final ResourceRepository store;

	public AbstractBusinessService(FhirContext fhirContext, ResourceRepository store) {
		this.fhirContext = fhirContext;
		this.store = store;
	}

	// --- CreateIfAbsent ---
	protected void createIfAbsent(Resource resource, Map<Resource, String> fullUrlMap) {
		String resourceType = resource.fhirType();
		String resourceId = RessourceUtil.extractId(resource, fullUrlMap);
		resource.setId(resourceId);
		List<ResourceEntity> existing = store.findByResourceTypeAndResourceId(resourceType,
				RessourceUtil.removeUrn(resourceId));
		if (existing == null || existing.isEmpty()) {
			ResourceEntity entity = new ResourceEntity();
			entity.setResourceType(resourceType);
			entity.setResourceId(RessourceUtil.removeUrn(resourceId));
			entity.setJson(fhirContext.newJsonParser().encodeResourceToString(resource));

			RessourceUtil.getIdentifiers(resource).forEach(identifier -> {
				entity.addIdentifier(
						new ResourceIdentifier().setIdSystem(identifier.getSystem()).setIdValue(identifier.getValue()).setResourceEntity(entity) );
			});

			store.save(entity);
			log.info("Created absent {} id={}", resourceType, resourceId);
		}
	}

}
