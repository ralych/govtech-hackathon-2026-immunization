package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ch.hl7.vacd.api.repo.ResourceRepository;
import org.hl7.fhir.r4.model.Practitioner;
import org.springframework.stereotype.Component;

@Component
public class PractitionerProvider extends BaseResourceProvider<Practitioner> {

    public PractitionerProvider(FhirContext fhirContext, ResourceRepository store) {
        super(fhirContext, store, Practitioner.class);
    }
}
