package com.dkozel.velocitylimiter.api;

import com.dkozel.velocitylimiter.domain.LoadCommand;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.regex.Pattern;

public record LoadRequest(
        String id,
        @JsonProperty("customer_id") String customerId,
        @JsonProperty("load_amount") String loadAmount,
        Instant time) {

    // digits only, exactly 2 decimals, at most 17 integer digits
    private static final Pattern AMOUNT = Pattern.compile("\\d{1,17}\\.\\d{2}");

    public LoadCommand toCommand() {
        if (!loadAmount.startsWith("$")) {
            throw new IllegalArgumentException("load_amount must start with '$': " + loadAmount);
        }
        String digits = loadAmount.substring(1);
        // the pattern rules out negatives and exponents; zero is the one non-positive value it lets through
        if (!AMOUNT.matcher(digits).matches() || new BigDecimal(digits).signum() == 0) {
            throw new IllegalArgumentException(
                    "load_amount must be a positive amount with exactly 2 decimals: " + loadAmount);
        }
        return new LoadCommand(customerId, id, new BigDecimal(digits), time);
    }
}
