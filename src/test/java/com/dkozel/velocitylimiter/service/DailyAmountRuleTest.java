package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.Period;
import com.dkozel.velocitylimiter.domain.Usage;
import com.dkozel.velocitylimiter.domain.VelocityLimits;
import com.dkozel.velocitylimiter.domain.Violation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DailyAmountRuleTest {

    // not the shipped 5000/20000/3: a rule that hard-codes the limit instead of reading it fails
    private static final VelocityLimits LIMITS =
            new VelocityLimits(new BigDecimal("100.00"), new BigDecimal("300.00"), 2);

    private static final Usage USED_80_TODAY = new Usage(new BigDecimal("80.00"), 1);

    private final DailyAmountRule rule = new DailyAmountRule();

    @Test
    void appliesToTheDay() {
        assertThat(rule.period()).isEqualTo(Period.DAY);
    }

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
