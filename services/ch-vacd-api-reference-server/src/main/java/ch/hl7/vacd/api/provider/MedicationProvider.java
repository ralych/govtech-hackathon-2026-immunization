package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ch.hl7.vacd.api.repo.ResourceRepository;
import org.hl7.fhir.r4.model.Medication;
import org.springframework.stereotype.Component;

@Component
public class MedicationProvider extends BaseResourceProvider<Medication> {

    public MedicationProvider(FhirContext fhirContext, ResourceRepository store) {
        super(fhirContext, store, Medication.class);
    }
}
