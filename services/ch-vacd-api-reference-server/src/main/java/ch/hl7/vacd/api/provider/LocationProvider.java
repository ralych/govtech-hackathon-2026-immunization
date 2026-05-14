package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ch.hl7.vacd.api.repo.ResourceRepository;
import org.hl7.fhir.r4.model.Location;
import org.springframework.stereotype.Component;

@Component
public class LocationProvider extends BaseResourceProvider<Location> {

    public LocationProvider(FhirContext fhirContext, ResourceRepository store) {
        super(fhirContext, store, Location.class);
    }
}
