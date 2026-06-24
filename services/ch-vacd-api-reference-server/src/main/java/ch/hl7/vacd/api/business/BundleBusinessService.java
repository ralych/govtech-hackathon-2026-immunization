
package ch.hl7.vacd.api.business;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;

import ch.hl7.vacd.api.exceptions.PatientNotFoundException;

/**
 * 
 */
public interface BundleBusinessService {

	Bundle createBundle(Bundle bundle) throws PatientNotFoundException;

	Bundle readBundle(IdType id);

	List<Bundle> searchBundles();

}
