package ch.bff.producer;

import ca.uhn.fhir.context.FhirContext;
import ch.bff.producer.client.FhirClient;
import ch.bff.producer.provider.models.ImmunizationCreateDto;
import ch.bff.producer.provider.models.PractitionerDto;
import ch.bff.producer.provider.models.RouteOfAdministration;
import ch.bff.producer.provider.models.VaccinationDto;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.PositiveIntType;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.SimpleQuantity;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

@Service
public class ImmunizationAdministrationService {

    private static final Logger log = LoggerFactory.getLogger(ImmunizationAdministrationService.class);

    private final FhirClient fhirClient;
    private final FhirContext fhirContext;

    public ImmunizationAdministrationService(FhirClient fhirClient) {
        this.fhirClient = fhirClient;
        this.fhirContext = FhirContext.forR4();
    }

    public VaccinationDto createImmunizationAdministration(
            String patientIamId, ImmunizationCreateDto createDto) {

        var patientUuid = UUID.fromString(patientIamId);
        var immunizationUuid = UUID.randomUUID();
        var practitionerUuid = UUID.randomUUID();
        var organizationUuid = UUID.randomUUID();
        var practitionerRoleUuid = UUID.randomUUID();
        var compositionUuid = UUID.randomUUID();

        // 1. Real patient from FHIR
        var fhirPatient = fhirClient.getPatientById(patientIamId);
        var patient = copyPatient(fhirPatient, patientUuid);

        // 2. Build FHIR resources
        var immunization = buildImmunization(createDto, immunizationUuid, patientUuid, practitionerUuid);
        var practitioner = buildPractitioner(practitionerUuid);
        var organization = buildOrganization(organizationUuid);
        var practitionerRole = buildPractitionerRole(practitionerRoleUuid, practitionerUuid, organizationUuid);
        var composition = buildComposition(compositionUuid, patientUuid, practitionerRoleUuid, immunizationUuid);

        // 3. Assemble Bundle
        var bundle = new Bundle();
        bundle.setId(UUID.randomUUID().toString());
        bundle.setType(Bundle.BundleType.DOCUMENT);
        bundle.setTimestamp(new Date());
        bundle.getMeta().addProfile("http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-document-immunization-administration");
        bundle.setIdentifier(new Identifier()
                .setSystem("urn:ietf:rfc:3986")
                .setValue("urn:uuid:" + bundle.getId()));

        bundle.addEntry(new BundleEntryComponent().setFullUrl("urn:uuid:" + compositionUuid).setResource(composition));
        bundle.addEntry(new BundleEntryComponent().setFullUrl("urn:uuid:" + patientUuid).setResource(patient));
        bundle.addEntry(new BundleEntryComponent().setFullUrl("urn:uuid:" + immunizationUuid).setResource(immunization));
        bundle.addEntry(new BundleEntryComponent().setFullUrl("urn:uuid:" + practitionerUuid).setResource(practitioner));
        bundle.addEntry(new BundleEntryComponent().setFullUrl("urn:uuid:" + organizationUuid).setResource(organization));
        bundle.addEntry(new BundleEntryComponent().setFullUrl("urn:uuid:" + practitionerRoleUuid).setResource(practitionerRole));

        // 4. Post to FHIR server
        var response = fhirClient.postImmunizationAdministrationBundle(bundle);
        log.info("Posted Immunization Administration Bundle, response ID: {}", response.getIdElement().getIdPart());

        // 5. Build VaccinationDto
        return buildVaccinationDto(createDto, immunizationUuid);
    }

    // ---- Resource builders ----

    private Patient copyPatient(Patient source, UUID patientUuid) {
        var json = fhirContext.newJsonParser().encodeResourceToString(source);
        var copy = fhirContext.newJsonParser().parseResource(Patient.class, json);
        copy.setId("urn:uuid:" + patientUuid);
        return copy;
    }

    private Immunization buildImmunization(ImmunizationCreateDto dto, UUID immunizationUuid,
                                           UUID patientUuid, UUID practitionerUuid) {
        var imm = new Immunization();
        imm.setId("urn:uuid:" + immunizationUuid);
        imm.setStatus(Immunization.ImmunizationStatus.COMPLETED);
        imm.getMeta().addProfile("http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-immunization-administration");

        var vaccineCode = new CodeableConcept();
        vaccineCode.addCoding(new Coding()
                .setSystem("urn:oid:1.2.3.4.5.6.7")
                .setCode(dto.vaccineCode())
                .setDisplay(dto.vaccineName()));
        vaccineCode.setText(dto.vaccineName());
        imm.setVaccineCode(vaccineCode);

        imm.setOccurrence(new DateTimeType(Date.from(dto.vaccinationDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())));

        var manufacturerRef = new Reference();
        manufacturerRef.setDisplay(dto.marketingAuthorizationHolder());
        imm.setManufacturer(manufacturerRef);

        imm.setLotNumber(dto.lotNumber());

        imm.setRoute(routeToConcept(dto.routeOfAdministration()));

        var site = new CodeableConcept();
        site.setText(dto.siteOfAdministration());
        imm.setSite(site);

        var doseQty = new SimpleQuantity();
        doseQty.setValue(dto.administeredDose().value());
        doseQty.setUnit(dto.administeredDose().unit());
        imm.setDoseQuantity(doseQty);

        imm.getPatient().setReference("urn:uuid:" + patientUuid);

        var performer = imm.addPerformer();
        performer.getActor().setReference("urn:uuid:" + practitionerUuid);
        performer.getActor().setDisplay("Dr. med. Sarah Müller");

        var pa = imm.addProtocolApplied();
        pa.setDoseNumber(new PositiveIntType(dto.doseNumber()));
        if (dto.seriesDoses() != null) {
            pa.setSeriesDoses(new PositiveIntType(dto.seriesDoses()));
        }

        if (dto.vaccinationReason() != null) {
            var reason = new CodeableConcept();
            reason.addCoding(new Coding()
                    .setSystem("http://snomed.info/sct")
                    .setCode(dto.vaccinationReason().code()));
            reason.setText(dto.vaccinationReason().display());
            imm.addReasonCode(reason);
        }

        return imm;
    }

    private Practitioner buildPractitioner(UUID practitionerUuid) {
        var p = new Practitioner();
        p.setId("urn:uuid:" + practitionerUuid);
        p.addIdentifier()
                .setSystem("urn:oid:2.51.1.3")
                .setValue("7601000123456");
        p.addName().setFamily("Müller").addGiven("Sarah");
        return p;
    }

    private Organization buildOrganization(UUID organizationUuid) {
        var org = new Organization();
        org.setId("urn:uuid:" + organizationUuid);
        org.addIdentifier()
                .setSystem("urn:oid:2.51.1.3")
                .setValue("7601000999999");
        org.setName("Praxis am Bahnhof");
        return org;
    }

    private PractitionerRole buildPractitionerRole(UUID roleUuid, UUID practitionerUuid, UUID organizationUuid) {
        var pr = new PractitionerRole();
        pr.setId("urn:uuid:" + roleUuid);
        pr.getPractitioner().setReference("urn:uuid:" + practitionerUuid);
        pr.getOrganization().setReference("urn:uuid:" + organizationUuid);
        return pr;
    }

    private Composition buildComposition(UUID compositionUuid, UUID patientUuid,
                                         UUID practitionerRoleUuid, UUID immunizationUuid) {
        var comp = new Composition();
        comp.setId("urn:uuid:" + compositionUuid);
        comp.getMeta().addProfile("http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-composition-immunization-administration");

        comp.setIdentifier(new Identifier()
                .setSystem("urn:ietf:rfc:3986")
                .setValue("urn:uuid:" + compositionUuid));

        comp.setStatus(Composition.CompositionStatus.FINAL);

        var type = new CodeableConcept();
        type.addCoding(new Coding("http://snomed.info/sct", "41000179103", "Immunization record"));
        comp.setType(type);

        var category = new CodeableConcept();
        category.addCoding(new Coding("urn:oid:2.16.756.5.30.1.127.3.10.10",
                "urn:che:epr:ch-vacd:immunization-administration:2022",
                "CH VACD Immunization Administration"));
        comp.addCategory(category);

        comp.setTitle("Immunization Administration");
        comp.setDate(new Date());

        comp.getSubject().setReference("urn:uuid:" + patientUuid);
        comp.addAuthor().setReference("urn:uuid:" + practitionerRoleUuid);

        var section = comp.addSection();
        section.setTitle("Immunization Administration");
        section.getCode().addCoding(new Coding("http://loinc.org", "11369-6", "Immunization Administration"));
        section.addEntry().setReference("urn:uuid:" + immunizationUuid);

        return comp;
    }

    // ---- helpers ----

    private VaccinationDto buildVaccinationDto(ImmunizationCreateDto dto, UUID immunizationUuid) {
        return new VaccinationDto(
                immunizationUuid,
                dto.vaccineName(),
                dto.vaccineCode(),
                dto.doseNumber() + "/" + (dto.seriesDoses() != null ? dto.seriesDoses() : "-"),
                dto.vaccinationDate(),
                dto.marketingAuthorizationHolder(),
                dto.lotNumber(),
                dto.routeOfAdministration().toString().toLowerCase(),
                dto.siteOfAdministration(),
                new PractitionerDto("Dr. med. Sarah Müller", "7601000123456"),
                dto.vaccinationReason()
        );
    }

    private static CodeableConcept routeToConcept(RouteOfAdministration route) {
        var cc = new CodeableConcept();
        switch (route) {
            case IM -> cc.addCoding(new Coding("http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration", "IM", "Intramuscular"));
            case SC -> cc.addCoding(new Coding("http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration", "SC", "Subcutaneous"));
            case ID -> cc.addCoding(new Coding("http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration", "ID", "Intradermal"));
            case ORAL -> cc.addCoding(new Coding("http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration", "ORAL", "Oral"));
            case NASAL -> cc.addCoding(new Coding("http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration", "NASAL", "Nasal"));
        }
        cc.setText(route.name());
        return cc;
    }
}
