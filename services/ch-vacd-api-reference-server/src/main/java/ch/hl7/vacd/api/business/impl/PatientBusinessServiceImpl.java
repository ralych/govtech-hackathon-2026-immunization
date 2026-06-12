/**
 * Author: Roeland Luykx
 * 
 * Copyright (c) 2026+ by RALY GmbH
 */

package ch.hl7.vacd.api.business.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdVaccinationRecordDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.param.StringParam;
import ch.hl7.vacd.api.business.PatientBusinessService;
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.repo.ResourceRepository;

/**
 * 
 */
@Service
public class PatientBusinessServiceImpl implements PatientBusinessService {

	@Autowired
	private FhirContext fhirContext;
	@Autowired
	private ResourceRepository store;
	@Autowired
	private EhrbaseClient ehrbaseClient;

	@Override
	public Patient createPatient(Patient patient) {
		String type = patient.fhirType();
		String json = fhirContext.newJsonParser().encodeResourceToString(patient);
		String id = patient.getIdElement() != null && patient.getIdElement().hasIdPart()
				? patient.getIdElement().getIdPart()
				: UUID.randomUUID().toString();
		patient.setId(type + "/" + id);
		String ehrId = ehrbaseClient.findOrCreateEhr(id);
		patient.addIdentifier().setSystem("urn:che:epr:ch-vacd:ehr-id").setValue("urn:uuid:" + ehrId);
		json = fhirContext.newJsonParser().encodeResourceToString(patient);

		ResourceEntity entity = new ResourceEntity();
		entity.setResourceType(type);
		entity.setResourceId(id);
		entity.setJson(json);
		store.save(entity);
		return patient;
	}

	@Override
	public Patient updatedPatient(Patient patient) {
		String type = patient.fhirType();
		String idPart = patient.getIdPart();
		String ehrId = ehrbaseClient.findOrCreateEhr(idPart);
		patient.addIdentifier().setSystem("urn:che:epr:ch-vacd:ehr-id").setValue("urn:uuid:" + ehrId);
		String json = fhirContext.newJsonParser().encodeResourceToString(patient);
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
							? (patient.getIdElement() != null ? patient.getIdElement().getIdPart()
									: UUID.randomUUID().toString())
							: idPart);
			entity.setJson(json);
		}
		store.save(entity);
		patient.setId(entity.getResourceType() + "/" + entity.getResourceId());

		return patient;

	}

	@Override
	public Patient readPatient(IdType theId) {
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

	@Override
	public List<Patient> searchPatient(StringParam name) {
		List<ResourceEntity> stored = store.findByResourceType("Patient").stream().filter(e -> {
			if (name == null || name.isEmpty())
				return true;
			try {
				IBaseResource r = fhirContext.newJsonParser().parseResource(e.getJson());
				if (r instanceof Patient) {
					Patient p = (Patient) r;
					return p.getName().stream().anyMatch(n -> n.getFamily().equalsIgnoreCase(name.getValue())
							|| n.getGiven().stream().anyMatch(g -> g.getValue().equalsIgnoreCase(name.getValue())));
				}
			} catch (Exception ex) {
				return false;
			}
			return false;
		}).toList();
		List<Patient> out = new ArrayList<>();
		for (ResourceEntity e : stored) {
			out.add((Patient) fhirContext.newJsonParser().parseResource(e.getJson()));
		}
		return out;
	}

	@Override
	public Bundle exportDocument(IdType thePatientId, Parameters parameters) {

		if (false) {
			ChVacdVaccinationRecordDocument chVaccinationRecordDocument = new ChVacdVaccinationRecordDocument();
			{
				Patient patient = readPatient(thePatientId);
				chVaccinationRecordDocument.setPatient(patient);
			}

			return chVaccinationRecordDocument;
		}
		Bundle b = new Bundle();
		b.setId(UUID.randomUUID().toString());
		b.setType(Bundle.BundleType.DOCUMENT);
		return b;
	}

}
