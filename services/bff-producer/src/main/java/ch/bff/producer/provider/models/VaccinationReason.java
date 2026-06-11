package ch.bff.producer.provider.models;

public record VaccinationReason(String code, String display, String swissLabel) {
    public VaccinationReason {
        if (code == null || code.isBlank())
            throw new IllegalArgumentException("Code ist ein Pflichtfeld");
    }
}
