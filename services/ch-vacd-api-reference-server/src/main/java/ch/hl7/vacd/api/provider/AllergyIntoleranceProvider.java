package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ch.hl7.vacd.api.repo.ResourceRepository;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.springframework.stereotype.Component;

@Component
public class AllergyIntoleranceProvider extends BaseResourceProvider<AllergyIntolerance> {

    public AllergyIntoleranceProvider(FhirContext fhirContext, ResourceRepository store) {
        super(fhirContext, store, AllergyIntolerance.class);
    }
}
