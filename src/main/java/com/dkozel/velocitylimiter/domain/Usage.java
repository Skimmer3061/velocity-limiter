package com.dkozel.velocitylimiter.domain;

import java.math.BigDecimal;

// a customer's accepted loads in one period window
public record Usage(BigDecimal amount, long count) {
}
