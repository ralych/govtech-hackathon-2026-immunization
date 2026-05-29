package ch.bff.producer.client;

import org.hl7.fhir.r4.model.Bundle;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "fhir-server", url = "${FHIR_BASE_URL:http://localhost:9111/ch-vacd-api-reference-server/fhir}")
public interface FhirClient {

    @GetMapping("/Patient")
    Bundle getPatient();

    /**
     * Beispiel: https://fhir.ch/ig/ch-vacd/4.0.0/Bundle-1-3-VaccinationRecord.json.html
     * @return Bundle with all vaccinations (VaccinationRecord)
     */
    @GetMapping("/Immunization")
    Bundle getVaccinationRecord(@RequestParam("patient") String patientIamId);

}
