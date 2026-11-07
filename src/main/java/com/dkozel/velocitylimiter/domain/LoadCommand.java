package com.dkozel.velocitylimiter.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record LoadCommand(String customerId, String id, BigDecimal amount, Instant loadedAt) {

    public LoadCommand {
        if (amount.signum() <= 0 || amount.scale() != 2) {
            throw new IllegalArgumentException("amount must be positive with exactly 2 decimals: " + amount);
        }
        // the database rounds to microseconds, which could move 23:59:59.9999996 into the next day; truncating can't
        loadedAt = loadedAt.truncatedTo(ChronoUnit.MICROS);
    }
}
