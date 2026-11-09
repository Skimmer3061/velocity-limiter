package com.dkozel.velocitylimiter.domain;

// what LoadService did with a load: decided it now, or recognised a repeat of an earlier one
public sealed interface LoadOutcome {

    record Decided(LoadDecision decision) implements LoadOutcome {
    }

    // same customer, id, amount and time: a retry, answered with the original decision
    record Replayed(LoadDecision original) implements LoadOutcome {
    }

    // same customer and id, different amount or time
    record Conflicting() implements LoadOutcome {
    }
}
