package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.LoadCommand;
import com.dkozel.velocitylimiter.domain.LoadDecision;
import com.dkozel.velocitylimiter.domain.LoadOutcome;
import com.dkozel.velocitylimiter.repository.LoadAttemptRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class LoadServiceConcurrencyIntegrationTest {

    private static final String CUSTOMER = "race-customer";
    private static final Instant WEDNESDAY = Instant.parse("2000-01-05T10:00:00Z");
    private static final BigDecimal AMOUNT = new BigDecimal("3000.00");

    @Autowired
    LoadService service;

    @Autowired
    JdbcClient jdbcClient;

    @MockitoSpyBean
    LoadAttemptRepository repository;

    @AfterEach
    void deleteCommittedRows() {
        for (String sql : List.of(
                "DELETE FROM load_attempt_violation WHERE customer_id = :customerId",
                "DELETE FROM load_attempt WHERE customer_id = :customerId",
                "DELETE FROM customer WHERE id = :customerId")) {
            jdbcClient.sql(sql).param("customerId", CUSTOMER).update();
        }
    }

    @ParameterizedTest(name = "customer row exists before: {0}")
    @ValueSource(booleans = {false, true})
    void acceptsOnlyOneOfTwoConcurrentLoadsThatTogetherExceedTheDailyLimit(boolean customerExists) throws Exception {
        if (customerExists) {
            // committed up front: the row already exists, so neither load creates it
            jdbcClient.sql("INSERT INTO customer (id) VALUES (:id)").param("id", CUSTOMER).update();
        }
        // without the lock both loads would read $0 here
        doAnswer(waitForTheOtherLoad()).when(repository).usage(any(), any());

        List<LoadOutcome> outcomes = processConcurrently(
                new LoadCommand(CUSTOMER, "1", AMOUNT, WEDNESDAY),
                new LoadCommand(CUSTOMER, "2", AMOUNT, WEDNESDAY));

        List<Boolean> accepted = outcomes.stream()
                .map(outcome -> ((LoadOutcome.Decided) outcome).decision().accepted())
                .toList();
        // which one wins depends on which thread locks first; that exactly one wins does not
        assertThat(accepted).containsExactlyInAnyOrder(true, false);
        // each decision was committed as returned (both lists in id order), and the race left one customer row
        assertThat(jdbcClient.sql("SELECT accepted FROM load_attempt WHERE customer_id = :customerId ORDER BY id")
                .param("customerId", CUSTOMER)
                .query(Boolean.class)
                .list()).containsExactlyElementsOf(accepted);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM customer WHERE id = :customerId")
                .param("customerId", CUSTOMER)
                .query(Long.class)
                .single()).isOne();
    }

    @Test
    void ignoresTheSecondOfTwoConcurrentLoadsWithTheSameId() throws Exception {
        // without the lock both loads would pass the duplicate check, and the second insert would hit the primary key
        doAnswer(waitForTheOtherLoad()).when(repository).find(any(), any());
        LoadCommand load = new LoadCommand(CUSTOMER, "1", new BigDecimal("100.00"), WEDNESDAY);

        assertThat(processConcurrently(load, load))
                .containsExactlyInAnyOrder(new LoadOutcome.Decided(new LoadDecision(CUSTOMER, "1", true)),
                        new LoadOutcome.Replayed(new LoadDecision(CUSTOMER, "1", true)));
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM load_attempt WHERE customer_id = :customerId")
                .param("customerId", CUSTOMER)
                .query(Long.class)
                .single()).isOne();
    }

    // results in the order the loads were given; a failure inside a thread is rethrown by get()
    private List<LoadOutcome> processConcurrently(LoadCommand first, LoadCommand second)
            throws Exception {
        List<Future<LoadOutcome>> results;
        try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
            results = threads.invokeAll(List.of(() -> service.process(first), () -> service.process(second)));
        }
        List<LoadOutcome> outcomes = new ArrayList<>();
        for (Future<LoadOutcome> result : results) {
            outcomes.add(result.get());
        }
        return outcomes;
    }

    // each call waits, after the real method, until the other load has made the same call (or the wait runs out);
    // the wait stays under the 1 s lock timeout the other load may be waiting on
    private static Answer<Object> waitForTheOtherLoad() {
        CountDownLatch bothCalled = new CountDownLatch(2);
        return invocation -> {
            Object result = invocation.callRealMethod();
            bothCalled.countDown();
            bothCalled.await(300, TimeUnit.MILLISECONDS);
            return result;
        };
    }
}
