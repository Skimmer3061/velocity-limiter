package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.LoadCommand;
import com.dkozel.velocitylimiter.domain.LoadDecision;
import com.dkozel.velocitylimiter.domain.LoadOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// limits far below the shipped ones: each test declines a load the shipped limits would accept,
// so it fails if the service hard-codes that limit instead of reading the configuration
@SpringBootTest(properties = {
        "velocity.limits.daily-amount=10.00",
        "velocity.limits.weekly-amount=15.00",
        "velocity.limits.daily-count=1"
})
@Transactional
class LoadServiceConfiguredLimitsIntegrationTest {

    // Wednesday 2000-01-05, inside the week Mon 01-03 to Mon 01-10
    private static final Instant WEDNESDAY = Instant.parse("2000-01-05T10:00:00Z");

    @Autowired
    LoadService service;

    @Test
    void appliesTheConfiguredDailyAmount() {
        assertThat(service.process(load("configured-daily", "1", "10.01", WEDNESDAY)))
                .isEqualTo(decided("configured-daily", "1", false));
    }

    @Test
    void appliesTheConfiguredWeeklyAmount() {
        assertThat(service.process(load("configured-weekly", "1", "10.00", WEDNESDAY)))
                .isEqualTo(decided("configured-weekly", "1", true));

        // the next day: within the daily limits, 15.01 for the week
        assertThat(service.process(load("configured-weekly", "2", "5.01", WEDNESDAY.plusSeconds(86_400))))
                .isEqualTo(decided("configured-weekly", "2", false));
    }

    @Test
    void appliesTheConfiguredDailyCount() {
        assertThat(service.process(load("configured-count", "1", "1.00", WEDNESDAY)))
                .isEqualTo(decided("configured-count", "1", true));

        assertThat(service.process(load("configured-count", "2", "1.00", WEDNESDAY.plusSeconds(1))))
                .isEqualTo(decided("configured-count", "2", false));
    }

    private static LoadCommand load(String customerId, String id, String amount, Instant time) {
        return new LoadCommand(customerId, id, new BigDecimal(amount), time);
    }

    private static LoadOutcome decided(String customerId, String id, boolean accepted) {
        return new LoadOutcome.Decided(new LoadDecision(customerId, id, accepted));
    }
}
