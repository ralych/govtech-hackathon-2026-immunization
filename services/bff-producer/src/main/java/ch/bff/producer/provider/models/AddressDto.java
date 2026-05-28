package ch.bff.producer.provider.models;

// Zugehöriges Record für die Adresse
public record AddressDto(
        String street,
        String zipCode,
        String city
) {
}
