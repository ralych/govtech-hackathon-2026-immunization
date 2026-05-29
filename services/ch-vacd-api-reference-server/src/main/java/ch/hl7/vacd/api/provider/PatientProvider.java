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
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.entity.ResourceEntity;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class PatientProvider implements IResourceProvider {

	private final FhirContext fhirContext;
	private final ResourceRepository store;
	private final EhrbaseClient ehrbaseClient;
	private static final Logger log = LoggerFactory.getLogger(PatientProvider.class);

	public PatientProvider(FhirContext fhirContext, ResourceRepository store, EhrbaseClient ehrbaseClient) {
		this.fhirContext = fhirContext;
		this.store = store;
		this.ehrbaseClient = ehrbaseClient;
	}

	@Create
	public MethodOutcome create(@ResourceParam Patient patient) {
		String type = patient.fhirType();
		String json = fhirContext.newJsonParser().encodeResourceToString(patient);
		String id = patient.getIdElement() != null && patient.getIdElement().hasIdPart()
				? patient.getIdElement().getIdPart()
				: UUID.randomUUID().toString();
		patient.setId(type + "/" + id);
		String ehrId = ehrbaseClient.findOrCreateEhr(id);
		patient.addIdentifier()
			.setSystem("urn:che:epr:ch-vacd:ehr-id")
			.setValue("urn:uuid:" + ehrId);
		json = fhirContext.newJsonParser().encodeResourceToString(patient);

		ResourceEntity entity = new ResourceEntity();
		entity.setResourceType(type);
		entity.setResourceId(id);
		entity.setJson(json);
		store.save(entity);

		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType("Patient", id));
		outcome.setResource(patient);
		return outcome;
	}

	@Update
	public MethodOutcome update(@IdParam IdType id, @ResourceParam Patient resource) {
		String type = resource.fhirType();
		String idPart = id.getIdPart();
		String ehrId = ehrbaseClient.findOrCreateEhr(idPart);
		resource.addIdentifier()
			.setSystem("urn:che:epr:ch-vacd:ehr-id")
			.setValue("urn:uuid:" + ehrId);
		String json = fhirContext.newJsonParser().encodeResourceToString(resource);
		List<ResourceEntity> found = store.findByResourceTypeAndResourceId(type, idPart);
		ResourceEntity entity;
		if (found != null && !found.isEmpty()) {
			entity = found.get(0);
			entity.setJson(json);
		} else {
			entity = new ResourceEntity();
			entity.setResourceType(type);
			entity.setResourceId(
					idPart == null || idPart.isEmpty()
							? (resource.getIdElement() != null ? resource.getIdElement().getIdPart()
									: UUID.randomUUID().toString())
							: idPart);
			entity.setJson(json);
		}
		store.save(entity);
		resource.setId(entity.getResourceType() + "/" + entity.getResourceId());
		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType(resource.fhirType(), entity.getResourceId()));
		outcome.setResource(resource);
		outcome.setCreated(found == null || found.isEmpty());
		return outcome;
	}

	@Read
	public Patient read(@IdParam IdType theId) {
		List<ResourceEntity> found = store.findByResourceTypeAndResourceId("Patient", theId.getIdPart());
		if (found != null && !found.isEmpty()) {
			IBaseResource r = (IBaseResource) fhirContext.newJsonParser().parseResource(found.get(0).getJson());
			if (r != null)
				return (Patient) r;
		}
		Patient p = new Patient();
		p.setId(theId);
		p.addName().setFamily("Test").addGiven("Patient");
		return p;
	}

	@Search
	public List<Patient> search(@OptionalParam(name = "name") StringParam name) {
		List<ResourceEntity> stored = store.findByResourceType("Patient").stream()
				.filter(e -> {
					if (name == null || name.isEmpty()) return true;
					try {
						IBaseResource r = fhirContext.newJsonParser().parseResource(e.getJson());
						if (r instanceof Patient) {
							Patient p = (Patient) r;
							return p.getName().stream()
									.anyMatch(n -> n.getFamily().equalsIgnoreCase(name.getValue())
											|| n.getGiven().stream()
													.anyMatch(g -> g.getValue().equalsIgnoreCase(name.getValue())));
						}
					} catch (Exception ex) {
						return false;
					}
					return false;
				})
				.toList();
		List<Patient> out = new ArrayList<>();
		for (ResourceEntity e : stored) {
			out.add((Patient) fhirContext.newJsonParser().parseResource(e.getJson()));
		}
		return out;
	}

	@Operation(name = "export-document", idempotent = false)
	public Bundle exportDocument(@IdParam IIdType theId, @ResourceParam Parameters parameters) {
		if ((parameters.getParameter("type") != null) && //
				(parameters.getParameter("type").getValue() instanceof Coding) && //
				("urn:oid:2.16.756.5.30.1.127.3.10.10".equals(//
						((Coding) parameters.getParameter("type").getValue()).getSystem()))
				&& //
				("urn:che:epr:ch-vacd:vaccination-record:2022".equals(//
						((Coding) parameters.getParameter("type").getValue()).getCode()))//
		) {

			// In a real implementation, you would retrieve the patient and related resources based on the provided ID
			Bundle bundle = new Bundle();
			bundle.setType(Bundle.BundleType.DOCUMENT);
			return bundle;
		} else {
			throw new IllegalArgumentException("Unsupported type code!");
		}
	}

	@Override
	public Class<Patient> getResourceType() {
		return Patient.class;
	}
}