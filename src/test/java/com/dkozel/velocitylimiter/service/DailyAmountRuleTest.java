package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.CustomerUsage;
import com.dkozel.velocitylimiter.domain.VelocityLimits;
import com.dkozel.velocitylimiter.domain.Violation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DailyAmountRuleTest {

    // not the shipped 5000/20000/3: a rule that hard-codes the limit instead of reading it fails
    private static final VelocityLimits LIMITS =
            new VelocityLimits(new BigDecimal("100.00"), new BigDecimal("300.00"), 2);

    // the week differs from the day, so a rule that reads the wrong field fails
    private static final CustomerUsage USED_80_TODAY =
            new CustomerUsage(new BigDecimal("80.00"), new BigDecimal("250.00"), 1);

    private final DailyAmountRule rule = new DailyAmountRule();

    @Test
    void allowsALoadThatReachesTheLimitExactly() {
        assertThat(rule.check(new BigDecimal("20.00"), USED_80_TODAY, LIMITS)).isEmpty();
    }

    @Test
    void declinesALoadOneCentOverTheLimit() {
        assertThat(rule.check(new BigDecimal("20.01"), USED_80_TODAY, LIMITS))
                .contains(Violation.DAILY_AMOUNT);
    }
}
