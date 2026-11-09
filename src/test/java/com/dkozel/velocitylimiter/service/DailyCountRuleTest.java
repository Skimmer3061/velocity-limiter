package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.Period;
import com.dkozel.velocitylimiter.domain.Usage;
import com.dkozel.velocitylimiter.domain.VelocityLimits;
import com.dkozel.velocitylimiter.domain.Violation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DailyCountRuleTest {

    // not the shipped 5000/20000/3: a rule that hard-codes the limit instead of reading it fails
    private static final VelocityLimits LIMITS =
            new VelocityLimits(new BigDecimal("100.00"), new BigDecimal("300.00"), 2);

    // a tiny amount: the count limit applies "regardless of amount" (task text)
    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

    private final DailyCountRule rule = new DailyCountRule();

    @Test
    void appliesToTheDay() {
        assertThat(rule.period()).isEqualTo(Period.DAY);
    }

    @Test
    void allowsTheLastLoadTheLimitPermits() {
        var oneLoadToday = new Usage(new BigDecimal("0.01"), 1);

        assertThat(rule.check(ONE_CENT, oneLoadToday, LIMITS)).isEmpty();
    }

    @Test
    void declinesOneLoadOverTheLimit() {
        var twoLoadsToday = new Usage(new BigDecimal("0.02"), 2);

        assertThat(rule.check(ONE_CENT, twoLoadsToday, LIMITS)).contains(Violation.DAILY_COUNT);
    }
}
