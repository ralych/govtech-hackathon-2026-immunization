package ch.bff.producer.client;

import org.hl7.fhir.r4.model.Bundle;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "fhir-server", url = "${FHIR_BASE_URL:http://localhost:9111/ch-vacd-api-reference-server/fhir}")
public interface FhirClient {

    @GetMapping("/Patient")
    Bundle getPatient();
}
