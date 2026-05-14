package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ch.hl7.vacd.api.repo.ResourceRepository;
import ch.hl7.vacd.api.entity.ResourceEntity;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationDefinition;
import org.springframework.stereotype.Component;

@Component
public class OperationDefinitionProvider implements IResourceProvider {

    private final FhirContext fhirContext;
    private final ResourceRepository store;

    public OperationDefinitionProvider(FhirContext fhirContext, ResourceRepository store) {
        this.fhirContext = fhirContext;
        this.store = store;
    }

    @Read
    public OperationDefinition read(@IdParam IdType id) {
        java.util.List<ResourceEntity> found = store.findByResourceTypeAndResourceId("OperationDefinition", id.getIdPart());
        if (found != null && !found.isEmpty()) {
            org.hl7.fhir.instance.model.api.IBaseResource r = (org.hl7.fhir.instance.model.api.IBaseResource) fhirContext.newJsonParser().parseResource(found.get(0).getJson());
            if (r != null) return (OperationDefinition) r;
        }
        return new OperationDefinition();
    }

    @Override
    public Class<OperationDefinition> getResourceType() {
        return OperationDefinition.class;
    }
}
