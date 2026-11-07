package com.dkozel.velocitylimiter.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record LoadCommand(String customerId, String id, BigDecimal amount, Instant loadedAt) {
}
