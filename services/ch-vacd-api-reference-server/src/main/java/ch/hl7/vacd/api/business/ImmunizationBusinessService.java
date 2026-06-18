package ch.hl7.vacd.api.business;

import java.util.List;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Immunization;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunization;

import ca.uhn.fhir.rest.param.ReferenceParam;

public interface ImmunizationBusinessService {

	Immunization createImmunization(ChVacdImmunization immunization);

	Immunization readImmunization(IdType id);

	Immunization updateImmunization(ChVacdImmunization resource);

	List<Immunization> searchImmunizations(ReferenceParam patient);

}
