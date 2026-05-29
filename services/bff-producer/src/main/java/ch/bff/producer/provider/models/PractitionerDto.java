package ch.bff.producer.provider.models;

// Zugehöriges Record für die medizinische Fachperson (Arzt)
public record PractitionerDto(
        String doctorName,
        String gln
) {
    public PractitionerDto {
        if (doctorName == null || doctorName.isBlank()) {
            throw new IllegalArgumentException("Arztname darf nicht leer sein");
        }
        if (gln != null && !gln.matches("^7601\\d{9}$")) {
            throw new IllegalArgumentException("Ungültiges GLN-Format (muss eine 13-stellige Zahl beginnend mit 7601 sein)");
        }
    }
}
