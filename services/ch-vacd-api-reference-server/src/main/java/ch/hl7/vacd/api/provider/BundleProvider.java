package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.repo.ResourceRepository;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
