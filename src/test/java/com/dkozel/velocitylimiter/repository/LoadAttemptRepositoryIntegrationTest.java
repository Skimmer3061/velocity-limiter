package com.dkozel.velocitylimiter.repository;

import com.dkozel.velocitylimiter.domain.LoadAttempt;
import com.dkozel.velocitylimiter.domain.Period;
import com.dkozel.velocitylimiter.domain.Usage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class LoadAttemptRepositoryIntegrationTest {

    private static final String CUSTOMER = "usage-customer";

    // Wednesday 2000-01-05: day [01-05, 01-06), week [Mon 01-03, Mon 01-10)
    private static final Instant WEDNESDAY = Instant.parse("2000-01-05T12:00:00Z");

    @Autowired
    LoadAttemptRepository repository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void sumsOnlyAcceptedLoadsOfTheCustomerInsideEachWindow() {
        insert(CUSTOMER, "1", "50.00", "2000-01-05T00:00:00Z", true);   // day start is inside: day + week
        insert(CUSTOMER, "2", "100.00", "2000-01-05T10:00:00Z", true);  // day + week
        insert(CUSTOMER, "3", "200.00", "2000-01-05T11:00:00Z", false); // declined: never counts
        insert(CUSTOMER, "4", "300.00", "2000-01-03T00:00:00Z", true);  // week start is inside: week only
        insert(CUSTOMER, "5", "600.00", "2000-01-06T00:00:00Z", true);  // day end is outside: week only
        insert(CUSTOMER, "6", "400.00", "2000-01-02T23:59:59Z", true);  // previous Sunday: outside
        insert(CUSTOMER, "7", "500.00", "2000-01-10T00:00:00Z", true);  // week end is outside
        insert("other-customer", "8", "700.00", "2000-01-05T10:00:00Z", true);

        Usage day = repository.usage(CUSTOMER, Period.DAY.windowContaining(WEDNESDAY));
        Usage week = repository.usage(CUSTOMER, Period.WEEK.windowContaining(WEDNESDAY));

        assertThat(day.amount()).isEqualByComparingTo("150.00");
        assertThat(day.count()).isEqualTo(2);
        assertThat(week.amount()).isEqualByComparingTo("1050.00");
        assertThat(week.count()).isEqualTo(4);
    }

    @Test
    void findsAStoredAttemptByCustomerAndId() {
        insert(CUSTOMER, "1", "50.00", "2000-01-05T10:00:00Z", false);

        assertThat(repository.find(CUSTOMER, "1"))
                .contains(new LoadAttempt(CUSTOMER, "1", new BigDecimal("50.00"), Instant.parse("2000-01-05T10:00:00Z"), false));
        assertThat(repository.find(CUSTOMER, "2")).isEmpty();
        assertThat(repository.find("other-customer", "1")).isEmpty();
    }

    @Test
    void returnsZeroUsageForACustomerWithNoLoads() {
        Usage usage = repository.usage("new-customer", Period.WEEK.windowContaining(WEDNESDAY));

        assertThat(usage.amount()).isEqualByComparingTo("0");
        assertThat(usage.count()).isZero();
    }

    @Test
    void storesTheLargestAmountTheRequestGuardAllows() {
        // LoadRequest allows at most 17 integer digits because amount is DECIMAL(19,2)
        insert(CUSTOMER, "1", "99999999999999999.99", "2000-01-05T10:00:00Z", false);

        BigDecimal stored = jdbcClient.sql("SELECT amount FROM load_attempt WHERE customer_id = :customerId")
                .param("customerId", CUSTOMER)
                .query(BigDecimal.class)
                .single();
        assertThat(stored).isEqualByComparingTo("99999999999999999.99");
    }

    private void insert(String customerId, String id, String amount, String time, boolean accepted) {
        customerRepository.lock(customerId); // the foreign key needs the customer row, as in the service
        repository.insert(new LoadAttempt(customerId, id, new BigDecimal(amount), Instant.parse(time), accepted));
    }
}
