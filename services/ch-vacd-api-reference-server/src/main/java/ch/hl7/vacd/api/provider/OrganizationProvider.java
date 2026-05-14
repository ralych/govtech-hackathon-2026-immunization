package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ch.hl7.vacd.api.repo.ResourceRepository;
import org.hl7.fhir.r4.model.Organization;
import org.springframework.stereotype.Component;

@Component
public class OrganizationProvider extends BaseResourceProvider<Organization> {

    public OrganizationProvider(FhirContext fhirContext, ResourceRepository store) {
        super(fhirContext, store, Organization.class);
    }
}
