package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ch.hl7.vacd.api.repo.ResourceRepository;
import org.hl7.fhir.r4.model.Condition;
import org.springframework.stereotype.Component;

@Component
public class ConditionProvider extends BaseResourceProvider<Condition> {

    public ConditionProvider(FhirContext fhirContext, ResourceRepository store) {
        super(fhirContext, store, Condition.class);
    }
}
