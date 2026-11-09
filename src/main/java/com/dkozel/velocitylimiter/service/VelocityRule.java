package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.Period;
import com.dkozel.velocitylimiter.domain.Usage;
import com.dkozel.velocitylimiter.domain.VelocityLimits;
import com.dkozel.velocitylimiter.domain.Violation;

import java.math.BigDecimal;
import java.util.Optional;

interface VelocityRule {

    // the period whose usage this rule is checked against
    Period period();

    Optional<Violation> check(BigDecimal amount, Usage usage, VelocityLimits limits);
}
