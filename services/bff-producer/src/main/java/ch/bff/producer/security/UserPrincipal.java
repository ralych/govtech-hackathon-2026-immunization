package ch.bff.producer.security;

public record UserPrincipal(String sub, String userId, String role) {}
