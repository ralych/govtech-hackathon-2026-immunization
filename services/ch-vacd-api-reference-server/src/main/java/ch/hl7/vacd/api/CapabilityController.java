package ch.hl7.vacd.api;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

//@Controller
public class CapabilityController {

    @GetMapping(value = "/fhir/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> capabilityStatement() throws IOException {
        // Load an embedded CapabilityStatement JSON if present (fallback)
        ClassPathResource r = new ClassPathResource("CapabilityStatement-ch-vacd-api-capstmt-srv.json");
        if (r.exists()) {
            String json = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return ResponseEntity.ok(json);
        }
        // If not present, return a minimal statement (HAPI will provide a /metadata endpoint too)
        String minimal = "{\"resourceType\":\"CapabilityStatement\",\"status\":\"draft\"}";
        return ResponseEntity.ok(minimal);
    }
}
