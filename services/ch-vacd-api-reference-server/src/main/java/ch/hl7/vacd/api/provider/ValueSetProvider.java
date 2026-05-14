package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ch.hl7.vacd.api.repo.ResourceRepository;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.stereotype.Component;

@Component
public class ValueSetProvider extends BaseResourceProvider<ValueSet> {

    public ValueSetProvider(FhirContext fhirContext, ResourceRepository store) {
        super(fhirContext, store, ValueSet.class);
    }
}
