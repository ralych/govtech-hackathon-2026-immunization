package ch.bff.producer.client;

import org.hl7.fhir.r4.model.Patient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "fhir-server", url = "${FHIR_BASE_URL}")
public interface FhirClient {

    @GetMapping
    Patient getPatient();
}
