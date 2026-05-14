package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ch.hl7.vacd.api.repo.ResourceRepository;
import org.hl7.fhir.r4.model.CodeSystem;
import org.springframework.stereotype.Component;

@Component
public class CodeSystemProvider extends BaseResourceProvider<CodeSystem> {

    public CodeSystemProvider(FhirContext fhirContext, ResourceRepository store) {
        super(fhirContext, store, CodeSystem.class);
    }
}
