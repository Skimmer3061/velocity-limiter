package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.Period;
import com.dkozel.velocitylimiter.domain.Usage;
import com.dkozel.velocitylimiter.domain.VelocityLimits;
import com.dkozel.velocitylimiter.domain.Violation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyAmountRuleTest {

    // not the shipped 5000/20000/3: a rule that hard-codes the limit instead of reading it fails
    private static final VelocityLimits LIMITS =
            new VelocityLimits(new BigDecimal("100.00"), new BigDecimal("300.00"), 2);

    private static final Usage USED_280_THIS_WEEK = new Usage(new BigDecimal("280.00"), 3);

    private final WeeklyAmountRule rule = new WeeklyAmountRule();

    @Test
    void appliesToTheWeek() {
        assertThat(rule.period()).isEqualTo(Period.WEEK);
    }

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
