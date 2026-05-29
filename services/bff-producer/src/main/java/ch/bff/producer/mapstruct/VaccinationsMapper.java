package ch.bff.producer.mapstruct;

import ch.bff.producer.provider.models.PractitionerDto;
import ch.bff.producer.provider.models.VaccinationDto;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Immunization;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Mapper(componentModel = "spring", imports = {LocalDate.class, ZoneId.class, UUID.class})
public interface VaccinationsMapper {

    @Mapping(target = "id", expression = "java(UUID.fromString(immunization.getIdElement().getIdPart()))")
    @Mapping(target = "vaccineName", source = "vaccineCode", qualifiedByName = "vaccineName")
    @Mapping(target = "doseSequence", expression = "java(doseSequence(immunization))")
    @Mapping(target = "vaccinationDate", source = "occurrenceDateTimeType", qualifiedByName = "toLocalDate")
    @Mapping(target = "manufacturer", source = "manufacturer.display")
    @Mapping(target = "lotNumber", source = "lotNumber")
    @Mapping(target = "administrationRoute", source = "route", qualifiedByName = "conceptDisplay")
    @Mapping(target = "siteOfAdministration", source = "site", qualifiedByName = "conceptDisplay")
    @Mapping(target = "practitioner", expression = "java(mapPractitioner(immunization))")
    @Mapping(target = "reason", expression = "java(reasonText(immunization))")
    VaccinationDto toVaccinationDto(Immunization immunization);

    @Named("vaccineName")
    default String vaccineName(CodeableConcept vaccineCode) {
        if (vaccineCode == null) return null;
        if (vaccineCode.hasCoding()) {
            return vaccineCode.getCodingFirstRep().getDisplay();
        }
        return vaccineCode.getText();
    }

    @Named("toLocalDate")
    default LocalDate toLocalDate(DateTimeType dateTimeType) {
        if (dateTimeType == null) return null;
        Date date = dateTimeType.getValue();
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    @Named("conceptDisplay")
    default String conceptDisplay(CodeableConcept cc) {
        if (cc == null) return null;
        if (cc.hasCoding()) {
            return cc.getCodingFirstRep().getDisplay();
        }
        return cc.getText();
    }

    default String doseSequence(Immunization immunization) {
        var protocolApplied = immunization.getProtocolApplied();
        if (protocolApplied == null || protocolApplied.isEmpty()) return null;
        var pa = protocolApplied.get(0);
        int dose = pa.getDoseNumberPositiveIntType().getValue();
        Integer series = pa.hasSeriesDosesPositiveIntType()
                ? pa.getSeriesDosesPositiveIntType().getValue()
                : null;
        return series != null ? dose + "/" + series : String.valueOf(dose);
    }

    default PractitionerDto mapPractitioner(Immunization immunization) {
        var performers = immunization.getPerformer();
        if (performers == null || performers.isEmpty()) return null;
        var performer = performers.get(0);
        var actor = performer.getActor();
        if (actor == null) return null;

        String name = actor.getDisplay();
        String gln = null;
        if (actor.hasIdentifier()) {
            gln = actor.getIdentifier().getValue();
        }
        return new PractitionerDto(name, gln);
    }

    default String reasonText(Immunization immunization) {
        var reasonCodes = immunization.getReasonCode();
        if (reasonCodes == null || reasonCodes.isEmpty()) return null;
        var reason = reasonCodes.get(0);
        if (reason.hasText()) return reason.getText();
        if (reason.hasCoding()) return reason.getCodingFirstRep().getDisplay();
        return null;
    }
}
