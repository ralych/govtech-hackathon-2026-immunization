package ch.hl7.vacd.api.business;

import java.util.List;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Immunization;

import ca.uhn.fhir.rest.param.ReferenceParam;

public interface ImmunizationBusinessService {

	Immunization createImmunization(Immunization immunization);

	Immunization readImmunization(IdType id);

	Immunization updateImmunization(Immunization resource);

	List<Immunization> searchImmunizations(ReferenceParam patient);

}
