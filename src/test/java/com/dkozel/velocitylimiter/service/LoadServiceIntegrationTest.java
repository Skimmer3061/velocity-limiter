package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.LoadAttempt;
import com.dkozel.velocitylimiter.domain.LoadCommand;
import com.dkozel.velocitylimiter.domain.LoadDecision;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class LoadServiceIntegrationTest {

    private static final Instant TIME = Instant.parse("2000-01-01T00:00:00Z");
    // Wednesday 2000-01-05, inside the week Mon 01-03 to Mon 01-10
    private static final Instant WEDNESDAY = Instant.parse("2000-01-05T10:00:00Z");

    @Autowired
    LoadService service;

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void ignoresARepeatedIdForTheSameCustomer() {
        service.process(load("dup-customer", "1", TIME));

        assertThat(service.process(load("dup-customer", "1", TIME.plusSeconds(1)))).isEmpty();
        // the first attempt stays as it was: one row, with the first load's time
        assertThat(jdbcClient.sql("SELECT * FROM load_attempt WHERE customer_id = 'dup-customer'")
                .query(LoadAttempt.class)
                .single())
                .isEqualTo(new LoadAttempt("dup-customer", "1", new BigDecimal("100.00"), TIME, true));
    }

    @Test
    void answersTheSameIdForAnotherCustomer() {
        service.process(load("dup-customer-a", "1", TIME));

        assertThat(service.process(load("dup-customer-b", "1", TIME)))
                .contains(new LoadDecision("dup-customer-b", "1", true));
    }

    private static LoadCommand load(String customerId, String id, Instant time) {
        return new LoadCommand(customerId, id, new BigDecimal("100.00"), time);
    }

    @Test
    void declinesAndRecordsEveryViolatedRule() {
        assertAccepted(load("all-rules", "1", "1000.00", WEDNESDAY));
        assertAccepted(load("all-rules", "2", "1000.00", WEDNESDAY.plusSeconds(1)));
        assertAccepted(load("all-rules", "3", "1000.00", WEDNESDAY.plusSeconds(2)));

        // 4th load of the day and 5,500 in total: two limits at once
        assertThat(service.process(load("all-rules", "4", "2500.00", WEDNESDAY.plusSeconds(3))))
                .contains(new LoadDecision("all-rules", "4", false));
        assertThat(violationsOf("all-rules", "4")).containsExactlyInAnyOrder("DAILY_AMOUNT", "DAILY_COUNT");
    }

    @Test
    void ignoresARepeatOfADeclinedLoad() {
        assertThat(service.process(load("declined-repeat", "1", "5000.01", WEDNESDAY)))
                .contains(new LoadDecision("declined-repeat", "1", false));

        assertThat(service.process(load("declined-repeat", "1", "1.00", WEDNESDAY.plusSeconds(1)))).isEmpty();
    }

    @Test
    void declinedLoadsDoNotCountTowardsTheLimits() {
        assertThat(service.process(load("declined-free", "1", "5000.01", WEDNESDAY)))
                .contains(new LoadDecision("declined-free", "1", false));

        assertThat(service.process(load("declined-free", "2", "5000.00", WEDNESDAY.plusSeconds(1))))
                .contains(new LoadDecision("declined-free", "2", true));
    }

    @Test
    void aLoadArrivingOutOfOrderSeesTheWholeDay() {
        service.process(load("out-of-order", "1", "4000.00", Instant.parse("2000-01-05T20:00:00Z")));

        // earlier in the same day than the load above: "up to now" would wrongly see 0 and accept it
        assertThat(service.process(load("out-of-order", "2", "1500.00", Instant.parse("2000-01-05T08:00:00Z"))))
                .contains(new LoadDecision("out-of-order", "2", false));
        assertThat(violationsOf("out-of-order", "2")).containsExactly("DAILY_AMOUNT");
    }

    @Test
    void declinesTheFourthLoadOfTheDayOnTheCountAlone() {
        assertAccepted(load("count-only", "1", "1.00", WEDNESDAY));
        assertAccepted(load("count-only", "2", "1.00", WEDNESDAY.plusSeconds(1)));
        assertAccepted(load("count-only", "3", "1.00", WEDNESDAY.plusSeconds(2)));

        // $4 in total: far from every amount limit, so only the count can decline it
        assertThat(service.process(load("count-only", "4", "1.00", WEDNESDAY.plusSeconds(3))))
                .contains(new LoadDecision("count-only", "4", false));
        assertThat(violationsOf("count-only", "4")).containsExactly("DAILY_COUNT");
    }

    @Test
    void declinesOnTheWeeklyLimitAloneUntilTheNextMonday() {
        // Mon 01-03 to Thu 01-06: one 5,000 load a day, inside every daily limit, 20,000 for the week
        assertAccepted(load("weekly", "1", "5000.00", Instant.parse("2000-01-03T10:00:00Z")));
        assertAccepted(load("weekly", "2", "5000.00", Instant.parse("2000-01-04T10:00:00Z")));
        assertAccepted(load("weekly", "3", "5000.00", Instant.parse("2000-01-05T10:00:00Z")));
        assertAccepted(load("weekly", "4", "5000.00", Instant.parse("2000-01-06T10:00:00Z")));

        // last second of the week: a new day, but the same week
        assertThat(service.process(load("weekly", "5", "0.01", Instant.parse("2000-01-09T23:59:59Z"))))
                .contains(new LoadDecision("weekly", "5", false));
        assertThat(violationsOf("weekly", "5")).containsExactly("WEEKLY_AMOUNT");

        // one second later: a new week
        assertThat(service.process(load("weekly", "6", "5000.00", Instant.parse("2000-01-10T00:00:00Z"))))
                .contains(new LoadDecision("weekly", "6", true));
    }

    // setup loads must really be accepted, or the test would pass against other limits than the shipped ones
    private void assertAccepted(LoadCommand command) {
        assertThat(service.process(command)).contains(new LoadDecision(command.customerId(), command.id(), true));
    }

    private static LoadCommand load(String customerId, String id, String amount, Instant time) {
        return new LoadCommand(customerId, id, new BigDecimal(amount), time);
    }

    private List<String> violationsOf(String customerId, String id) {
        return jdbcClient.sql("""
                        SELECT rule FROM load_attempt_violation
                        WHERE customer_id = :customerId AND load_id = :id
                        """)
                .param("customerId", customerId)
                .param("id", id)
                .query(String.class)
                .list();
    }
}
