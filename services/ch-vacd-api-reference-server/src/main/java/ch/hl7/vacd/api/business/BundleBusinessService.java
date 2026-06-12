
package ch.hl7.vacd.api.business;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;

/**
 * 
 */
public interface BundleBusinessService {

	Bundle createBundle(Bundle bundle);

	Bundle readBundle(IdType id);

	List<Bundle> searchBundles();

}
