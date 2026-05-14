package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ch.hl7.vacd.api.repo.ResourceRepository;
import ch.hl7.vacd.api.entity.ResourceEntity;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Immunization;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ImmunizationProvider implements IResourceProvider {

    private final FhirContext fhirContext;
    private final ResourceRepository store;

    public ImmunizationProvider(FhirContext fhirContext, ResourceRepository store) {
        this.fhirContext = fhirContext;
        this.store = store;
    }

    @Create
    public MethodOutcome create(@ResourceParam Immunization immunization) {
        String type = immunization.fhirType();
        String id = immunization.getIdElement() != null && immunization.getIdElement().hasIdPart() ? immunization.getIdElement().getIdPart() : java.util.UUID.randomUUID().toString();
        immunization.setId(type + "/" + id);
        String json = fhirContext.newJsonParser().encodeResourceToString(immunization);
        ResourceEntity entity = new ResourceEntity();
        entity.setResourceType(type);
        entity.setResourceId(id);
        entity.setJson(json);
        store.save(entity);
        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType("Immunization", id));
        outcome.setResource(immunization);
        return outcome;
    }

    @Read
    public Immunization read(@IdParam IdType id) {
        java.util.List<ResourceEntity> found = store.findByResourceTypeAndResourceId("Immunization", id.getIdPart());
        if (found != null && !found.isEmpty()) {
            org.hl7.fhir.instance.model.api.IBaseResource r = (org.hl7.fhir.instance.model.api.IBaseResource) fhirContext.newJsonParser().parseResource(found.get(0).getJson());
            if (r != null) return (Immunization) r;
        }
        return new Immunization();
    }
    
    @Update
	public MethodOutcome update(@IdParam IdType id, @ResourceParam Immunization resource) {
		String type = resource.fhirType();
		String idPart = id.getIdPart();
		String json = fhirContext.newJsonParser().encodeResourceToString(resource);
		List<ResourceEntity> found = store.findByResourceTypeAndResourceId(type, idPart);
		ResourceEntity entity;
		if (found != null && !found.isEmpty()) {
			entity = found.get(0);
			entity.setJson(json);
		} else {
			entity = new ResourceEntity();
			entity.setResourceType(type);
			entity.setResourceId(
					idPart == null || idPart.isEmpty()
							? (resource.getIdElement() != null ? resource.getIdElement().getIdPart()
									: java.util.UUID.randomUUID().toString())
							: idPart);
			entity.setJson(json);
		}
		store.save(entity);
		resource.setId(entity.getResourceType() + "/" + entity.getResourceId());
		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType(resource.fhirType(), entity.getResourceId()));
		outcome.setResource(resource);
		outcome.setCreated(found == null || found.isEmpty());
		return outcome;
	}
    

    @Search
    public List<Immunization> search(@OptionalParam(name = "patient") ReferenceParam patient) {
        java.util.List<ResourceEntity> stored = store.findByResourceType("Immunization");
        List<Immunization> out = new ArrayList<>();
        for (ResourceEntity e : stored) {
            out.add((Immunization) fhirContext.newJsonParser().parseResource(e.getJson()));
        }
        return out;
    }

    @Override
    public Class<Immunization> getResourceType() {
        return Immunization.class;
    }
}