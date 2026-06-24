package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Search;
import ch.hl7.vacd.api.repo.ResourceRepository;

import java.util.List;

import org.hl7.fhir.r4.model.Practitioner;
import org.springframework.stereotype.Component;

@Component
public class PractitionerProvider extends BaseResourceProvider<Practitioner> {

    public PractitionerProvider(FhirContext fhirContext, ResourceRepository store) {
        super(fhirContext, store, Practitioner.class);
    }

	@Override
	@Search
	public List<Practitioner> search() {
		return super.search();
	}
    
    
}
