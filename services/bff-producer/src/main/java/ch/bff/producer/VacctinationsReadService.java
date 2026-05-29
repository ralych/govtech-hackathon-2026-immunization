package ch.bff.producer;

import ch.bff.producer.client.FhirClient;
import ch.bff.producer.mapstruct.VaccinationsMapper;
import ch.bff.producer.provider.models.VaccinationDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VacctinationsReadService {

    public static final String IMMUNIZATION_LOINC_CODE = "11369-6";
    private final FhirClient fhirClient;
    private final VaccinationsMapper vaccinationsMapper;

    public VacctinationsReadService(FhirClient fhirClient, VaccinationsMapper vaccinationsMapper) {
        this.fhirClient = fhirClient;
        this.vaccinationsMapper = vaccinationsMapper;
    }

    public List<VaccinationDto> getVaccinationList(String patientIamId) {
        var bundle = fhirClient.getVaccinationRecord(patientIamId);

        return bundle.getEntry().stream()
                .filter(entry -> entry.getResource() instanceof org.hl7.fhir.r4.model.Composition)
                .map(entry -> (org.hl7.fhir.r4.model.Composition) entry.getResource())
                .flatMap(composition -> composition.getSection().stream())
                .filter(section -> section.getCode().getCoding().stream()
                        .anyMatch(coding -> "http://loinc.org".equals(coding.getSystem()) && IMMUNIZATION_LOINC_CODE.equals(coding.getCode())))
                .flatMap(section -> section.getEntry().stream())
                .filter(entry -> entry.getResource() instanceof org.hl7.fhir.r4.model.Immunization)
                .map(entry -> (org.hl7.fhir.r4.model.Immunization) entry.getResource())
                .map(vaccinationsMapper::toVaccinationDto)
                .toList();
    }
}
