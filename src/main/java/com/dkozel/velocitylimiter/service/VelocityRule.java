package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.CustomerUsage;
import com.dkozel.velocitylimiter.domain.VelocityLimits;
import com.dkozel.velocitylimiter.domain.Violation;

import java.math.BigDecimal;
import java.util.Optional;

interface VelocityRule {

    Optional<Violation> check(BigDecimal amount, CustomerUsage usage, VelocityLimits limits);
}
