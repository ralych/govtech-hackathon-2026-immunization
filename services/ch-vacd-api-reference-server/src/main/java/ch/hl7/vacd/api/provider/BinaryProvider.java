package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ch.hl7.vacd.api.repo.ResourceRepository;
import org.hl7.fhir.r4.model.Binary;
import org.springframework.stereotype.Component;

@Component
public class BinaryProvider extends BaseResourceProvider<Binary> {

    public BinaryProvider(FhirContext fhirContext, ResourceRepository store) {
        super(fhirContext, store, Binary.class);
    }
}
