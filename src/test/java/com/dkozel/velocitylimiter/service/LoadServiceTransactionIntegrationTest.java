package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.LoadCommand;
import com.dkozel.velocitylimiter.repository.LoadAttemptRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

// not @Transactional: a test transaction would wrap the service's own, and the rollback under test would never happen
@SpringBootTest
class LoadServiceTransactionIntegrationTest {

    private static final String CUSTOMER = "tx-customer";

    @Autowired
    LoadService service;

    @Autowired
    JdbcClient jdbcClient;

    @MockitoSpyBean
    LoadAttemptRepository repository;

    @AfterEach
    void deleteCommittedRows() {
        jdbcClient.sql("DELETE FROM load_attempt_violation WHERE customer_id = :customerId")
                .param("customerId", CUSTOMER)
                .update();
        jdbcClient.sql("DELETE FROM load_attempt WHERE customer_id = :customerId")
                .param("customerId", CUSTOMER)
                .update();
        jdbcClient.sql("DELETE FROM customer WHERE id = :customerId")
                .param("customerId", CUSTOMER)
                .update();
    }

    @Test
    void rollsBackTheAttemptWhenItsViolationsCannotBeStored() {
        doAnswer(invocation -> {
            // runs inside the service's transaction, so it sees the attempt written just before
            assertThat(attemptRows()).isOne();
            throw new DataIntegrityViolationException("simulated failure");
        }).when(repository).insertViolations(any(), any(), any());
        // over the daily limit, so the attempt is declined and has a violation to store
        var declined = new LoadCommand(CUSTOMER, "1", new BigDecimal("5000.01"), Instant.parse("2000-01-05T10:00:00Z"));

        assertThatThrownBy(() -> service.process(declined))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("simulated failure");

        assertThat(attemptRows()).isZero();
    }

    private long attemptRows() {
        return jdbcClient.sql("SELECT COUNT(*) FROM load_attempt WHERE customer_id = :customerId")
                .param("customerId", CUSTOMER)
                .query(Long.class)
                .single();
    }
}
