package ch.bff.producer.provider;

import ch.bff.producer.provider.models.ImmunizationCreateDto;
import ch.bff.producer.provider.models.VaccinationDto;
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
@RequestMapping("/api/vaccinations")
public class VaccinationProvider {

    @GetMapping
    public List<VaccinationDto> getVaccinations(String personId) {
        System.out.println("Anfrage für Impfungen der Person mit ID: " + personId);
        return List.of(
                // 1. Impfung aus dem Screenshot
                new VaccinationDto(
                        UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
                        "Priorix",
                        "1/2",
                        LocalDate.of(2020, 1, 22),
                        "GlaxoSmithKline",
                        "PR2001",
                        "s.c.",
                        "Oberarm links",
                        new PractitionerDto("Dr. S. Müller", "7601000233456"),
                        "Grundimmunisierung"
                ),

                // 2. Impfung (Folgeimpfung MMR)
                new VaccinationDto(
                        UUID.fromString("7b921e12-3456-7890-abcd-ef1234567890"),
                        "Priorix",
                        "2/2",
                        LocalDate.of(2020, 3, 24),
                        "GlaxoSmithKline",
                        "PR2005",
                        "s.c.",
                        "Oberarm rechts",
                        new PractitionerDto("Dr. S. Müller", "7601000233456"),
                        "Auffrischimpfung"
                ),

                // 3. Impfung (Starrkrampf / DTP)
                new VaccinationDto(
                        UUID.fromString("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"),
                        "Boostrix",
                        "1/1",
                        LocalDate.of(2021, 6, 15),
                        "GlaxoSmithKline",
                        "BS9942",
                        "i.m.",
                        "Oberschenkel links",
                        new PractitionerDto("Dr. med. A. Pfister", "7601003445566"),
                        "Auffrischimpfung"
                ),

                // 4. Impfung (FSME / Zecken)
                new VaccinationDto(
                        UUID.fromString("f8e7d6c5-b4a3-9281-7065-5443322110aa"),
                        "FSME-Immun",
                        "1/3",
                        LocalDate.of(2023, 4, 10),
                        "Pfizer",
                        "VNR402A",
                        "i.m.",
                        "Oberarm links",
                        new PractitionerDto("Dr. S. Müller", "7601000233456"),
                        "Grundimmunisierung"
                ),

                // 5. Impfung (Grippe)
                new VaccinationDto(
                        UUID.fromString("00000000-1111-2222-3333-444455556666"),
                        "Fluarix Tetra",
                        "1/1",
                        LocalDate.of(2025, 10, 05),
                        "GlaxoSmithKline",
                        "FL2025X",
                        "i.m.",
                        "Oberarm rechts",
                        new PractitionerDto("Apotheke am Bahnhof", "7601009998877"),
                        "saisonale Influenza"
                )
        );
    }
}