package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.CustomerUsage;
import com.dkozel.velocitylimiter.domain.VelocityLimits;
import com.dkozel.velocitylimiter.domain.Violation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyAmountRuleTest {

    // not the shipped 5000/20000/3: a rule that hard-codes the limit instead of reading it fails
    private static final VelocityLimits LIMITS =
            new VelocityLimits(new BigDecimal("100.00"), new BigDecimal("300.00"), 2);

    // daily limit is no obstacle: only the week matters
    private static final CustomerUsage USED_280_THIS_WEEK =
            new CustomerUsage(BigDecimal.ZERO, new BigDecimal("280.00"), 0);

    private final WeeklyAmountRule rule = new WeeklyAmountRule();

    @Test
    void allowsALoadThatReachesTheLimitExactly() {
        assertThat(rule.check(new BigDecimal("20.00"), USED_280_THIS_WEEK, LIMITS)).isEmpty();
    }

    @Test
    void declinesALoadOneCentOverTheLimit() {
        assertThat(rule.check(new BigDecimal("20.01"), USED_280_THIS_WEEK, LIMITS))
                .contains(Violation.WEEKLY_AMOUNT);
    }
}
