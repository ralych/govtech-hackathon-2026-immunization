package ch.bff.producer.provider;

import ch.bff.producer.provider.models.ImmunizationCreateDto;
import ch.bff.producer.provider.models.VaccinationDto;
import ch.bff.producer.provider.models.PractitionerDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/immunizations")
public class ImmunizationProvider {

    // POST-Endpoint zum Erfassen einer neuen Impfung
    @PostMapping
    public ResponseEntity<VaccinationDto> createImmunization(
            @RequestParam String personId,
            @RequestBody ImmunizationCreateDto createDto) {

        // 1. Hier würde in der Praxis die Business-Logik stehen (z.B. immunizationService.save(...))
        // 2. Das Backend generiert die ID und zieht den Arzt/Zeitstempel aus der aktuellen Session

        System.out.println("Erfasse neue Impfung für Person-ID: " + personId);
        System.out.println("Impfstoff: " + createDto.vaccineName() + ", Charge: " + createDto.lotNumber());

        // Dummy-Rückgabe: Wir mappen die Create-Daten auf das Read-DTO (mit ID)
        VaccinationDto savedImmunization = new VaccinationDto(
                UUID.randomUUID(), // Vom Server generierte ID
                createDto.vaccineName(),
                createDto.doseNumber() + "/" + (createDto.seriesDoses() != null ? createDto.seriesDoses() : "-"),
                createDto.vaccinationDate(),
                createDto.marketingAuthorizationHolder(),
                createDto.lotNumber(),
                createDto.routeOfAdministration().toString().toLowerCase(),
                createDto.siteOfAdministration(),
                new PractitionerDto("Dr. med. Sarah Müller", "7601000123456"), // Aus dem Signatur-Feld des Formulars
                createDto.reason()
        );

        // Gebe HTTP 201 Created und das gespeicherte Objekt zurück
        return ResponseEntity.status(HttpStatus.CREATED).body(savedImmunization);
    }

}