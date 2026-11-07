package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.CustomerUsage;
import com.dkozel.velocitylimiter.domain.VelocityLimits;
import com.dkozel.velocitylimiter.domain.Violation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
class DailyCountRule implements VelocityRule {

    @Override
    public Optional<Violation> check(BigDecimal amount, CustomerUsage usage, VelocityLimits limits) {
        boolean overLimit = usage.dailyCount() + 1 > limits.dailyCount();
        return overLimit ? Optional.of(Violation.DAILY_COUNT) : Optional.empty();
    }
}
