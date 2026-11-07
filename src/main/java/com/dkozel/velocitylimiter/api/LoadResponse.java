package com.dkozel.velocitylimiter.api;

import com.dkozel.velocitylimiter.domain.LoadDecision;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"id", "customer_id", "accepted"})
public record LoadResponse(String id, @JsonProperty("customer_id") String customerId, boolean accepted) {

    static LoadResponse from(LoadDecision decision) {
        return new LoadResponse(decision.id(), decision.customerId(), decision.accepted());
    }
}
