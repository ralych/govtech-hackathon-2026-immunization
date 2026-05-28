package ch.bff.producer.provider;

import ch.bff.producer.provider.models.ImmunizationCreateDto;
import ch.bff.producer.provider.models.ImmunizationDto;
import ch.bff.producer.provider.models.PractitionerDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/immunizations")
public class ImmunizationProvider {

    // POST-Endpoint zum Erfassen einer neuen Impfung
    @PostMapping
    public ResponseEntity<ImmunizationDto> createImmunization(
            @RequestParam String personId,
            @RequestBody ImmunizationCreateDto createDto) {

        // 1. Hier würde in der Praxis die Business-Logik stehen (z.B. immunizationService.save(...))
        // 2. Das Backend generiert die ID und zieht den Arzt/Zeitstempel aus der aktuellen Session

        System.out.println("Erfasse neue Impfung für Person-ID: " + personId);
        System.out.println("Impfstoff: " + createDto.vaccineName() + ", Charge: " + createDto.lotNumber());

        // Dummy-Rückgabe: Wir mappen die Create-Daten auf das Read-DTO (mit ID)
        ImmunizationDto savedImmunization = new ImmunizationDto(
                UUID.randomUUID(), // Vom Server generierte ID
                createDto.vaccineName(),
                createDto.doseNumber() + "/" + (createDto.seriesDoses() != null ? createDto.seriesDoses() : "-"),
                createDto.vaccinationDate(),
                createDto.marketingAuthorizationHolder(),
                createDto.lotNumber(),
                createDto.routeOfAdministration().toString().toLowerCase(),
                createDto.siteOfAdministration(),
                new PractitionerDto("Dr. med. Sarah Müller", "7601000123456") // Aus dem Signatur-Feld des Formulars
        );

        // Gebe HTTP 201 Created und das gespeicherte Objekt zurück
        return ResponseEntity.status(HttpStatus.CREATED).body(savedImmunization);
    }

    @GetMapping
    public List<ImmunizationDto> getImmunizations(String personId) {
        System.out.println("Anfrage für Impfungen der Person mit ID: " + personId);
        return List.of(
                // 1. Impfung aus dem Screenshot
                new ImmunizationDto(
                        UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
                        "Priorix",
                        "1/2",
                        LocalDate.of(2020, 1, 22),
                        "GlaxoSmithKline",
                        "PR2001",
                        "s.c.",
                        "Oberarm links",
                        new PractitionerDto("Dr. S. Müller", "7601000233456")
                ),

                // 2. Impfung (Folgeimpfung MMR)
                new ImmunizationDto(
                        UUID.fromString("7b921e12-3456-7890-abcd-ef1234567890"),
                        "Priorix",
                        "2/2",
                        LocalDate.of(2020, 3, 24),
                        "GlaxoSmithKline",
                        "PR2005",
                        "s.c.",
                        "Oberarm rechts",
                        new PractitionerDto("Dr. S. Müller", "7601000233456")
                ),

                // 3. Impfung (Starrkrampf / DTP)
                new ImmunizationDto(
                        UUID.fromString("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"),
                        "Boostrix",
                        "1/1",
                        LocalDate.of(2021, 6, 15),
                        "GlaxoSmithKline",
                        "BS9942",
                        "i.m.",
                        "Oberschenkel links",
                        new PractitionerDto("Dr. med. A. Pfister", "7601003445566")
                ),

                // 4. Impfung (FSME / Zecken)
                new ImmunizationDto(
                        UUID.fromString("f8e7d6c5-b4a3-9281-7065-5443322110aa"),
                        "FSME-Immun",
                        "1/3",
                        LocalDate.of(2023, 4, 10),
                        "Pfizer",
                        "VNR402A",
                        "i.m.",
                        "Oberarm links",
                        new PractitionerDto("Dr. S. Müller", "7601000233456")
                ),

                // 5. Impfung (Grippe)
                new ImmunizationDto(
                        UUID.fromString("00000000-1111-2222-3333-444455556666"),
                        "Fluarix Tetra",
                        "1/1",
                        LocalDate.of(2025, 10, 05),
                        "GlaxoSmithKline",
                        "FL2025X",
                        "i.m.",
                        "Oberarm rechts",
                        new PractitionerDto("Apotheke am Bahnhof", "7601009998877")
                )
        );
    }
}