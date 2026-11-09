package com.dkozel.velocitylimiter.api;

import com.dkozel.velocitylimiter.domain.LoadCommand;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

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
        @Pattern(regexp = "\\$(0\\.(?!00)\\d{2}|[1-9]\\d{0,16}\\.\\d{2})",
                message = "must be a positive dollar amount with exactly 2 decimals, like $123.45")
        String loadAmount,
        @NotNull Instant time) {

    // years 0001 to 9999, should be adjusted to needs
    @AssertTrue(message = "time must be between 0001-01-01 and 9999-12-31 UTC")
    boolean isTimeInSupportedRange() {
        return time == null || (!time.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
                && time.isBefore(Instant.parse("+10000-01-01T00:00:00Z")));
    }

    // for a validated request
    public LoadCommand toCommand() {
        return new LoadCommand(customerId, id, new BigDecimal(loadAmount.substring(1)), time);
    }
}
