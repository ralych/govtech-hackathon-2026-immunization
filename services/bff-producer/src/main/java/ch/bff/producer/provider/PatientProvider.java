package ch.bff.producer.provider;

import ch.bff.producer.provider.models.AddressDto;
import ch.bff.producer.provider.models.Gender;
import ch.bff.producer.provider.models.PatientDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/patients")
public class PatientProvider {
    @GetMapping
    public List<PatientDto> getPatients() {
        return getSamplePatients();
    }

    public static List<PatientDto> getSamplePatients() {
        return List.of(
                // 1. Patient aus dem Screenshot
                new PatientDto(
                        "Brunner",
                        "Noah",
                        LocalDate.of(2018, 12, 3),
                        7,
                        Gender.MÄNNLICH,
                        "756.6789.0123.45",
                        new AddressDto("Hauptstrasse 45", "8400", "Winterthur"),
                        "eltern.brunner@hotmail.com",
                        "+41 79 456 78 90"
                ),

                // 2. Patientin
                new PatientDto(
                        "Meier",
                        "Elena",
                        LocalDate.of(1992, 5, 14),
                        34,
                        Gender.WEIBLICH,
                        "756.3124.5589.12",
                        new AddressDto("Bahnhofstrasse 12", "8001", "Zürich"),
                        "elena.meier@gmx.ch",
                        "+41 44 211 33 44"
                ),

                // 3. Patient
                new PatientDto(
                        "Favre",
                        "Jean-Luc",
                        LocalDate.of(1965, 11, 22),
                        60,
                        Gender.MÄNNLICH,
                        "756.8941.2233.76",
                        new AddressDto("Rue du Simplon 5", "1006", "Lausanne"),
                        "jl.favre@bluewin.ch",
                        "+41 21 614 11 22"
                ),

                // 4. Patientin
                new PatientDto(
                        "Keller",
                        "Sarah",
                        LocalDate.of(2010, 8, 19),
                        15,
                        Gender.WEIBLICH,
                        "756.4452.9811.03",
                        new AddressDto("Grenzacherstrasse 8", "4058", "Basel"),
                        "sarah.keller@fhnw.ch",
                        "+41 61 324 88 99"
                ),

                // 5. Patient
                new PatientDto(
                        "Bernasconi",
                        "Matteo",
                        LocalDate.of(1987, 3, 30),
                        39,
                        Gender.MÄNNLICH,
                        "756.1298.7744.51",
                        new AddressDto("Via Nassa 24", "6900", "Lugano"),
                        "matteo.bernasconi@ticino.com",
                        "+41 91 923 55 66"
                )
        );
    }
}