package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ch.hl7.vacd.api.repo.ResourceRepository;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.springframework.stereotype.Component;

@Component
public class RelatedPersonProvider extends BaseResourceProvider<RelatedPerson> {

    public RelatedPersonProvider(FhirContext fhirContext, ResourceRepository store) {
        super(fhirContext, store, RelatedPerson.class);
    }
}
