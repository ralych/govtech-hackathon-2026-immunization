package ch.bff.producer.provider.models;

import java.time.LocalDate;

public record ImmunizationCreateDto(
        String vaccineName,
        String marketingAuthorizationHolder,
        String lotNumber,
        LocalDate expiryDate, // Optional laut Formular
        LocalDate vaccinationDate,
        RouteOfAdministration routeOfAdministration,
        AdministeredDose administeredDose,
        String siteOfAdministration,
        String reason,
        int doseNumber,
        Integer seriesDoses, // Integer statt int, da es für Booster leer (null) sein darf
        boolean adverseReactionObserved
) {
    // Kompakter Konstruktor für die Validierung der Pflichtfelder
    public ImmunizationCreateDto {
        if (vaccineName == null || vaccineName.isBlank()) throw new IllegalArgumentException("Impfstoffname ist ein Pflichtfeld");
        if (marketingAuthorizationHolder == null || marketingAuthorizationHolder.isBlank()) throw new IllegalArgumentException("Hersteller ist ein Pflichtfeld");
        if (lotNumber == null || lotNumber.isBlank()) throw new IllegalArgumentException("Chargennummer ist ein Pflichtfeld");
        if (vaccinationDate == null) throw new IllegalArgumentException("Impfdatum ist ein Pflichtfeld");
        if (routeOfAdministration == null) throw new IllegalArgumentException("Applikationsweg ist ein Pflichtfeld");
        if (administeredDose == null) throw new IllegalArgumentException("Verabreichte Menge ist ein Pflichtfeld");
        if (siteOfAdministration == null || siteOfAdministration.isBlank()) throw new IllegalArgumentException("Applikationsort ist ein Pflichtfeld");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Impfgrund ist ein Pflichtfeld");
        if (doseNumber < 1) throw new IllegalArgumentException("Dosisnummer muss mindestens 1 sein");
    }
}

