package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ch.hl7.vacd.api.repo.ResourceRepository;
import org.hl7.fhir.r4.model.Observation;
import org.springframework.stereotype.Component;

@Component
public class ObservationProvider extends BaseResourceProvider<Observation> {

    public ObservationProvider(FhirContext fhirContext, ResourceRepository store) {
        super(fhirContext, store, Observation.class);
    }
}
