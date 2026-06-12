package ch.hl7.vacd.api.provider;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ch.hl7.vacd.api.business.BundleBusinessService;
import ch.hl7.vacd.api.utils.RessourceUtil;

@Component
public class BundleProvider implements IResourceProvider {

	private static final String CH_VACD_BUNDLE_PROFILE = "http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-document-immunization-administration";

	private final BundleBusinessService bundleBusinessService;
	private final FhirContext fhirContext;

	public BundleProvider(FhirContext fhirContext, BundleBusinessService bundleBusinessService) {
		this.fhirContext = fhirContext;
		this.bundleBusinessService = bundleBusinessService;
	}

	@Override
	public Class<Bundle> getResourceType() {
		return Bundle.class;
	}

	// --- CRUD operations ---

	@Create
	public MethodOutcome create(@ResourceParam Bundle bundle) {
		// Ensure CH VACD profile is present in meta.
		bundle = RessourceUtil.ensureBundleProfile(bundle, CH_VACD_BUNDLE_PROFILE);

		Bundle retBundle = bundleBusinessService.createBundle(bundle);

		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType("Bundle", retBundle.getId()));
		outcome.setResource(retBundle);
		return outcome;
	}

	@Read
	public Bundle read(@IdParam IdType id) {

		return bundleBusinessService.readBundle(id);

	}

	@Search
	public List<Bundle> search() {
		return bundleBusinessService.searchBundles();

	}

}
