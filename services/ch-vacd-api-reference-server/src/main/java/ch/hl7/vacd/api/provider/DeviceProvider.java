package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ch.hl7.vacd.api.repo.ResourceRepository;
import org.hl7.fhir.r4.model.Device;
import org.springframework.stereotype.Component;

@Component
public class DeviceProvider extends BaseResourceProvider<Device> {

    public DeviceProvider(FhirContext fhirContext, ResourceRepository store) {
        super(fhirContext, store, Device.class);
    }
}
