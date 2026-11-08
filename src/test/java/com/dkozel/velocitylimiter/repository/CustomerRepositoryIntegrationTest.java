package com.dkozel.velocitylimiter.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CustomerRepositoryIntegrationTest {

    @Autowired
    CustomerRepository repository;

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void createsTheRowOnTheFirstLockAndReusesItAfter() {
        repository.lock("lock-customer");
        repository.lock("lock-customer");

        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM customer WHERE id = 'lock-customer'")
                .query(Long.class).single()).isOne();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void refusesToLockOutsideATransaction() {
        assertThatThrownBy(() -> repository.lock("lock-customer"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
