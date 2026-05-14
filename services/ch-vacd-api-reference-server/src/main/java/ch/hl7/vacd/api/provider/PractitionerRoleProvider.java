package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ch.hl7.vacd.api.repo.ResourceRepository;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.springframework.stereotype.Component;

@Component
public class PractitionerRoleProvider extends BaseResourceProvider<PractitionerRole> {

    public PractitionerRoleProvider(FhirContext fhirContext, ResourceRepository store) {
        super(fhirContext, store, PractitionerRole.class);
    }
}
