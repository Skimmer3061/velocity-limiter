package com.dkozel.velocitylimiter.api;

import com.dkozel.velocitylimiter.domain.LoadCommand;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

public record LoadRequest(
        String id,
        @JsonProperty("customer_id") String customerId,
        @JsonProperty("load_amount") String loadAmount,
        Instant time) {

    public LoadCommand toCommand() {
        if (!loadAmount.startsWith("$")) {
            throw new IllegalArgumentException("load_amount must start with '$': " + loadAmount);
        }
        return new LoadCommand(customerId, id, new BigDecimal(loadAmount.substring(1)), time);
    }
}
