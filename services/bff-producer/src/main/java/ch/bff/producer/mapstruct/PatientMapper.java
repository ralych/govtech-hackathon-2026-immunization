package ch.bff.producer.mapstruct;

import ch.bff.producer.provider.models.AddressDto;
import ch.bff.producer.provider.models.Gender;
import ch.bff.producer.provider.models.PatientDto;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Identifier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Mapper(componentModel = "spring", imports = {LocalDate.class, Period.class, ZoneId.class})
public interface PatientMapper {

    String AHV_SYSTEM = "urn:oid:2.16.756.5.30.1.123.100.1.1.1";

    @Mapping(target = "id", source = "idElement.idPart")
    @Mapping(target = "lastName", source = "nameFirstRep.family")
    @Mapping(target = "firstName", source = "nameFirstRep.givenAsSingleString")
    @Mapping(target = "birthDate", source = "birthDate", qualifiedByName = "toLocalDate")
    @Mapping(target = "age", expression = "java(patient.getBirthDate() != null ? Period.between(patient.getBirthDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now()).getYears() : 0)")
    @Mapping(target = "gender", source = "gender", qualifiedByName = "mapGender")
    @Mapping(target = "ahvNumber", source = "identifier", qualifiedByName = "extractAhv")
    @Mapping(target = "address", source = "addressFirstRep", qualifiedByName = "mapAddress")
    @Mapping(target = "email", source = "telecom", qualifiedByName = "extractEmail")
    @Mapping(target = "phoneNumber", source = "telecom", qualifiedByName = "extractPhone")
    PatientDto toPatientDto(org.hl7.fhir.r4.model.Patient patient);

    @Named("toLocalDate")
    default LocalDate toLocalDate(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    @Named("mapGender")
    default Gender mapGender(Enumerations.AdministrativeGender fhirGender) {
        if (fhirGender == null) return Gender.DIVERS;
        return switch (fhirGender) {
            case MALE -> Gender.MÄNNLICH;
            case FEMALE -> Gender.WEIBLICH;
            default -> Gender.DIVERS;
        };
    }

    @Named("extractAhv")
    default String extractAhv(List<Identifier> identifiers) {
        if (identifiers == null) return null;
        return identifiers.stream()
                .filter(id -> AHV_SYSTEM.equals(id.getSystem()))
                .findFirst()
                .map(Identifier::getValue)
                .orElseGet(() -> identifiers.stream()
                        .map(Identifier::getValue)
                        .filter(v -> v != null && v.matches("756[.-]?\\d{4}[.-]?\\d{4}[.-]?\\d{2}"))
                        .findFirst()
                        .orElse(null));
    }

    @Named("mapAddress")
    default AddressDto mapAddress(Address fhirAddress) {
        if (fhirAddress == null) return null;
        String street = fhirAddress.hasLine()
                ? String.join(" ", fhirAddress.getLine().stream().map(ln -> ln.getValueNotNull()).toList())
                : null;
        return new AddressDto(street, fhirAddress.getPostalCode(), fhirAddress.getCity());
    }

    @Named("extractEmail")
    default String extractEmail(List<ContactPoint> telecoms) {
        return extractTelecom(telecoms, ContactPoint.ContactPointSystem.EMAIL);
    }

    @Named("extractPhone")
    default String extractPhone(List<ContactPoint> telecoms) {
        return extractTelecom(telecoms, ContactPoint.ContactPointSystem.PHONE);
    }

    private static String extractTelecom(List<ContactPoint> telecoms, ContactPoint.ContactPointSystem system) {
        if (telecoms == null) return null;
        return telecoms.stream()
                .filter(cp -> system.equals(cp.getSystem()))
                .findFirst()
                .map(ContactPoint::getValue)
                .orElse(null);
    }
}
