package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ch.hl7.vacd.api.repo.ResourceRepository;
import ch.hl7.vacd.api.business.PatientBusinessService;
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.entity.ResourceEntity;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class PatientProvider implements IResourceProvider {

	
	@Autowired
	public PatientBusinessService patientBusinessService;
	
	private final FhirContext fhirContext;
//	private final ResourceRepository store;
//	private final EhrbaseClient ehrbaseClient;

	public PatientProvider(FhirContext fhirContext/*, ResourceRepository store, EhrbaseClient ehrbaseClient*/) {
		this.fhirContext = fhirContext;
//		this.store = store;
//		this.ehrbaseClient = ehrbaseClient;
	}

	@Create
	public MethodOutcome create(@ResourceParam Patient patient) {
		
		Patient createdPatient = patientBusinessService.createPatient(patient);

		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType("Patient", createdPatient.getId()));
		outcome.setResource(patient);
		return outcome;
	}

	@Update
	public MethodOutcome update(@IdParam IdType id, @ResourceParam Patient patient) {
		
		Patient updatedPatient = patientBusinessService.updatedPatient(patient);
		
		
		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType(updatedPatient.fhirType(), updatedPatient.getId()));
		outcome.setResource(updatedPatient);
		outcome.setCreated(updatedPatient == null || updatedPatient.isEmpty());
		return outcome;
	}

	@Read
	public Patient read(@IdParam IdType theId) {
		return patientBusinessService.readPatient(theId);
	}

	@Search
	public List<Patient> search(@OptionalParam(name = "name") StringParam name) {
		return patientBusinessService.searchPatient(name);
	}

	@Operation(name = "export-document", idempotent = false)
	public Bundle exportDocument(@IdParam IdType theId, @ResourceParam Parameters parameters) {
		if ((parameters.getParameter("type") != null) && //
				(parameters.getParameter("type").getValue() instanceof Coding) && //
				("urn:oid:2.16.756.5.30.1.127.3.10.10".equals(//
						((Coding) parameters.getParameter("type").getValue()).getSystem()))
				&& //
				("urn:che:epr:ch-vacd:vaccination-record:2022".equals(//
						((Coding) parameters.getParameter("type").getValue()).getCode()))//
		) {

			// In a real implementation, you would retrieve the patient and related resources based on the provided ID
			return patientBusinessService.exportDocument(theId, parameters);
		} else {
			throw new IllegalArgumentException("Unsupported type code!");
		}
	}

	@Override
	public Class<Patient> getResourceType() {
		return Patient.class;
	}
}