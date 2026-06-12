package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ch.hl7.vacd.api.business.BundleBusinessService;
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.client.FeederAuditEnricher;
import ch.hl7.vacd.api.client.OpenFhirClient;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.repo.ResourceRepository;
import ch.hl7.vacd.api.utils.RessourceUtil;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class BundleProvider implements IResourceProvider {

	private static final String CH_VACD_BUNDLE_PROFILE = "http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-document-immunization-administration";

	@Autowired
	private BundleBusinessService bundleBusinessService;
	
	private final FhirContext fhirContext;
//	private final ResourceRepository store;
//	private final EhrbaseClient ehrbaseClient;
//	private final OpenFhirClient openFhirClient;

	public BundleProvider(FhirContext fhirContext/*, ResourceRepository store, EhrbaseClient ehrbaseClient,
			OpenFhirClient openFhirClient*/) {
		this.fhirContext = fhirContext;
//		this.store = store;
//		this.ehrbaseClient = ehrbaseClient;
//		this.openFhirClient = openFhirClient;
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
