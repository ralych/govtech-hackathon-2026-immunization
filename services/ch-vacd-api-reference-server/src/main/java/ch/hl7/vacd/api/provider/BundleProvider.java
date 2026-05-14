package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.repo.ResourceRepository;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

@Component
public class BundleProvider implements IResourceProvider {

	private final FhirContext fhirContext;
	private final ResourceRepository store;

	public BundleProvider(FhirContext fhirContext, ResourceRepository store) {
		this.fhirContext = fhirContext;
		this.store = store;
	}
	
	@Create
	public MethodOutcome create(@ResourceParam Bundle bundle) {
		String type = bundle.fhirType();
		String id = bundle.getIdElement() != null && bundle.getIdElement().hasIdPart()
				? bundle.getIdElement().getIdPart()
				: java.util.UUID.randomUUID().toString();
		bundle.setId(type + "/" + id);
		String json = fhirContext.newJsonParser().encodeResourceToString(bundle);

		ResourceEntity entity = new ResourceEntity();
		entity.setResourceType(type);
		entity.setResourceId(id);
		entity.setJson(json);
		store.save(entity);

		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType("Bundle", id));
		outcome.setResource(bundle);
		return outcome;
	}

	@Override
	public Class<Bundle> getResourceType() {
		return Bundle.class;
	}
}
