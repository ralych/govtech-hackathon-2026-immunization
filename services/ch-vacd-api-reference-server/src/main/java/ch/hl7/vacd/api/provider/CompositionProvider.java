package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ch.hl7.vacd.api.repo.ResourceRepository;
import org.hl7.fhir.r4.model.Composition;
import org.springframework.stereotype.Component;

@Component
public class CompositionProvider extends BaseResourceProvider<Composition> {

    public CompositionProvider(FhirContext fhirContext, ResourceRepository store) {
        super(fhirContext, store, Composition.class);
    }
}
