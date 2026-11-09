package com.dkozel.velocitylimiter.api;

import com.dkozel.velocitylimiter.domain.LoadCommand;
import com.fasterxml.jackson.annotation.JsonProperty;
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
        @NotNull
        @SupportedTime
        Instant time) {

    // for a validated request
    public LoadCommand toCommand() {
        return new LoadCommand(customerId, id, new BigDecimal(loadAmount.substring(1)), time);
    }
}
