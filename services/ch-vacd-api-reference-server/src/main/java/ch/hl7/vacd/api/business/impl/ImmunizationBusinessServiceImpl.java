package ch.hl7.vacd.api.business.impl;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ch.hl7.vacd.api.business.ImmunizationBusinessService;
import ch.hl7.vacd.api.client.OpenFhirClient;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.repo.ResourceRepository;

@Service
public class ImmunizationBusinessServiceImpl extends AbstractBusinessService implements ImmunizationBusinessService {

	private static final Logger log = LoggerFactory.getLogger(ImmunizationBusinessServiceImpl.class);

	private final OpenFhirClient openFhirClient;

	public ImmunizationBusinessServiceImpl(FhirContext fhirContext, ResourceRepository store,
			OpenFhirClient openFhirClient) {
		super(fhirContext, store);
		this.openFhirClient = openFhirClient;
	}

	@Override
	public Immunization createImmunization(Immunization immunization) {
//		String type = immunization.fhirType();
//		String id = immunization.getIdElement() != null && immunization.getIdElement().hasIdPart()
//				? immunization.getIdElement().getIdPart()
//				: java.util.UUID.randomUUID().toString();
//		immunization.setId(id);
		
		createIfAbsent(immunization, new java.util.HashMap<>());
//		String json = fhirContext.newJsonParser().encodeResourceToString(immunization);
//		ResourceEntity entity = new ResourceEntity();
//		entity.setResourceType(type);
//		entity.setResourceId(id);
//		entity.setJson(json);
//		store.save(entity);

		return immunization;
	}

	@Override
	public Immunization readImmunization(IdType id) {
		List<ResourceEntity> found = store.findByResourceTypeAndResourceId("Immunization", id.getIdPart());
		if (found != null && !found.isEmpty()) {
			ResourceEntity entity = found.get(0);
			try {
				log.info(entity.getJson());
				return (Immunization) fhirContext.newJsonParser().parseResource(entity.getJson());
			} catch (Exception ex) {
				// Try to get the FHIR json from the endpoint
				String fhirJson = openFhirClient.toFhir(entity.getJson());
				log.info("Parsed FHIR JSON from openFHIR: " + fhirJson);
				try {
					return (Immunization) fhirContext.newJsonParser().parseResource(fhirJson);
				} catch (Exception ex2) {
					log.error("Failed to parse FHIR JSON for Immunization id={}: {}", id.getIdPart(), ex2.getMessage());
				}
			}
		}
		return new Immunization();
	}

	@Override
	public Immunization updateImmunization(Immunization resource) {
		String type = resource.fhirType();
		String idPart = resource.getIdPart();
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
									: java.util.UUID.randomUUID().toString())
							: idPart);
			entity.setJson(json);
		}
		store.save(entity);
		resource.setId(entity.getResourceId());
		return resource;
	}

	@Override
	public List<Immunization> searchImmunizations(ReferenceParam patient) {
		List<ResourceEntity> stored = store.findByResourceType("Immunization");
		List<Immunization> out = new ArrayList<>();
		for (ResourceEntity e : stored) {
			var imm = ((Immunization) fhirContext.newJsonParser().parseResource(e.getJson()));
			imm.setId(e.getResourceId());
			if (patient == null) {
				out.add(imm);
				continue;
			}
			if (imm.getPatient() == null || imm.getPatient().getReference() == null
					|| !imm.getPatient().getReference().equals("urn:uuid:" + patient.getValue())) {
				log.info("Skipping Immunization id={} due to patient reference mismatch: expected {}, actual {}",
						imm.getId(), "urn:uuid:" + patient.getValue(),
						imm.getPatient() != null ? imm.getPatient().getReference() : "null");
				continue;
			}

			String patientRef = imm.getPatient().getReference().substring("urn:uuid:".length());
			Patient p = store.findByResourceTypeAndResourceId("Patient", patientRef).stream().map(pe -> {
				try {
					return (Patient) fhirContext.newJsonParser().parseResource(pe.getJson());
				} catch (Exception ex) {
					log.error("Failed to parse Patient JSON for reference {}: {}", patientRef, ex.getMessage());
					return null;
				}
			}).filter(parsed -> parsed != null).findFirst().orElse(null);

			String practitionerRef = imm.getPerformer().isEmpty() ? null
					: imm.getPerformer().get(0).getActor().getReference().substring("urn:uuid:".length());
			Practitioner practitioner = store.findByResourceTypeAndResourceId("Practitioner", practitionerRef).stream()
					.map(pe -> {
						try {
							return (Practitioner) fhirContext.newJsonParser().parseResource(pe.getJson());
						} catch (Exception ex) {
							log.error("Failed to parse Practitioner JSON for reference {}: {}", practitionerRef,
									ex.getMessage());
							return null;
						}
					}).filter(parsed -> parsed != null).findFirst().orElse(null);

			log.info("Immunization id={} references patient with id={} and practitioner with id={}", imm.getId(),
					patientRef, practitionerRef);
			log.info("Patient resource: {}",
					p != null ? fhirContext.newJsonParser().encodeResourceToString(p) : "null");
			log.info("Practitioner resource: {}",
					practitioner != null ? fhirContext.newJsonParser().encodeResourceToString(practitioner) : "null");

			out.add(imm);
		}
		return out;
	}

}
