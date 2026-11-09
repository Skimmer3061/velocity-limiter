package com.dkozel.velocitylimiter.api;

import com.dkozel.velocitylimiter.domain.LoadCommand;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.regex.Pattern;

public record LoadRequest(
        @NotBlank
        @Size(max = 64)
        String id,
        @NotBlank
        @Size(max = 64)
        @JsonProperty("customer_id")
        String customerId,
        @NotNull
        @JsonProperty("load_amount")
        @jakarta.validation.constraints.Pattern(regexp = "\\$(0\\.(?!00)\\d{2}|[1-9]\\d{0,16}\\.\\d{2})",
                message = "must be a positive dollar amount with exactly 2 decimals, like $123.45")
        String loadAmount,
        @NotNull Instant time) {

    // digits only, exactly 2 decimals, at most 17 integer digits
    private static final Pattern AMOUNT = Pattern.compile("\\d{1,17}\\.\\d{2}");

    // years 0001 to 9999, should be adjusted to needs
    @AssertTrue(message = "time must be between 0001-01-01 and 9999-12-31 UTC")
    boolean isTimeInSupportedRange() {
        return time == null || (!time.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
                && time.isBefore(Instant.parse("+10000-01-01T00:00:00Z")));
    }

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
