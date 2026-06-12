package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ch.hl7.vacd.api.repo.ResourceRepository;
import ch.hl7.vacd.api.business.ImmunizationBusinessService;
import ch.hl7.vacd.api.client.OpenFhirClient;
import ch.hl7.vacd.api.entity.ResourceEntity;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ImmunizationProvider implements IResourceProvider {

	private ImmunizationBusinessService immunizationBusinessService;

	private final FhirContext fhirContext;

	private static final Logger log = LoggerFactory.getLogger(ImmunizationProvider.class);

	public ImmunizationProvider(FhirContext fhirContext, ImmunizationBusinessService immunizationBusinessService) {
		this.fhirContext = fhirContext;
		this.immunizationBusinessService = immunizationBusinessService;
	}

	@Create
	public MethodOutcome create(@ResourceParam Immunization immunization) {
		
		Immunization retImmunization = immunizationBusinessService.createImmunization(immunization);
		
		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType("Immunization", retImmunization.getId()));
		outcome.setResource(retImmunization);
		return outcome;
	}

	@Read
	public Immunization read(@IdParam IdType id) {
		
		return immunizationBusinessService.readImmunization(id);
	
	}

	@Update
	public MethodOutcome update(@IdParam IdType id, @ResourceParam Immunization resource) {
	
		Immunization retImmunization = immunizationBusinessService.updateImmunization(resource);
		
		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType(retImmunization.fhirType(), retImmunization.getId()));
		outcome.setResource(retImmunization);
		outcome.setCreated(retImmunization == null || retImmunization.isEmpty());
		return outcome;
	}

	@Search
	public List<Immunization> search(@OptionalParam(name = "patient") ReferenceParam patient) {
		
		return immunizationBusinessService.searchImmunizations(patient);
		
	
	}

	@Override
	public Class<Immunization> getResourceType() {
		return Immunization.class;
	}
}