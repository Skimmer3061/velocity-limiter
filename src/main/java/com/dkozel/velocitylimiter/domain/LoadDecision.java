package com.dkozel.velocitylimiter.domain;

public record LoadDecision(String customerId, String id, boolean accepted) {
}
