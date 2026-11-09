package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.Period;
import com.dkozel.velocitylimiter.domain.Usage;
import com.dkozel.velocitylimiter.domain.VelocityLimits;
import com.dkozel.velocitylimiter.domain.Violation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
class DailyAmountRule implements VelocityRule {

    @Override
    public Period period() {
        return Period.DAY;
    }

    @Override
    public Optional<Violation> check(BigDecimal amount, Usage usage, VelocityLimits limits) {
        boolean overLimit = usage.amount().add(amount).compareTo(limits.dailyAmount()) > 0;
        return overLimit ? Optional.of(Violation.DAILY_AMOUNT) : Optional.empty();
    }
}
