package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ch.hl7.vacd.api.repo.ResourceRepository;
import ch.hl7.vacd.api.entity.ResourceEntity;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;

import java.util.List;

public abstract class BaseResourceProvider<T extends IBaseResource> implements IResourceProvider {

    protected final FhirContext fhirContext;
    protected final ResourceRepository store;
    protected final Class<T> resourceClass;

    protected BaseResourceProvider(FhirContext fhirContext, ResourceRepository store, Class<T> resourceClass) {
        this.fhirContext = fhirContext;
        this.store = store;
        this.resourceClass = resourceClass;
    }

    @Create
    public MethodOutcome create(@ResourceParam T resource) {
        String type = resource.fhirType();
        String id = resource.getIdElement() != null && resource.getIdElement().hasIdPart() ? resource.getIdElement().getIdPart() : java.util.UUID.randomUUID().toString();
        resource.setId(type + "/" + id);
        String json = fhirContext.newJsonParser().encodeResourceToString(resource);
        ResourceEntity entity = new ResourceEntity();
        entity.setResourceType(type);
        entity.setResourceId(id);
        entity.setJson(json);
        store.save(entity);
        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType(resource.fhirType(), id));
        outcome.setResource(resource);
        return outcome;
    }

    @Read
    public T read(@IdParam IdType id) {
        String type = resourceClass.getSimpleName();
        List<ResourceEntity> found = store.findByResourceTypeAndResourceId(type, id.getIdPart());
        if (found == null || found.isEmpty()) return null;
        String json = found.get(0).getJson();
        IBaseResource r = (IBaseResource) fhirContext.newJsonParser().parseResource(json);
        return r == null ? null : resourceClass.cast(r);
    }

    @Update
    public MethodOutcome update(@IdParam IdType id, @ResourceParam T resource) {
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
            entity.setResourceId(idPart == null || idPart.isEmpty() ? (resource.getIdElement()!=null?resource.getIdElement().getIdPart():java.util.UUID.randomUUID().toString()) : idPart);
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
    public List<T> search() {
        List<ResourceEntity> entities = store.findByResourceType(resourceClass.getSimpleName());
        return entities.stream().map(e -> (IBaseResource) fhirContext.newJsonParser().parseResource(e.getJson())).map(resourceClass::cast).toList();
    }

    @Override
    public Class<T> getResourceType() {
        return resourceClass;
    }
}
