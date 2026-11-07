package com.dkozel.velocitylimiter.repository;

import com.dkozel.velocitylimiter.domain.CustomerUsage;
import com.dkozel.velocitylimiter.domain.LoadAttempt;
import com.dkozel.velocitylimiter.domain.UsageWindows;
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
    private static final UsageWindows WEDNESDAY = UsageWindows.containing(Instant.parse("2000-01-05T12:00:00Z"));

    @Autowired
    LoadAttemptRepository repository;

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

        CustomerUsage usage = repository.usage(CUSTOMER, WEDNESDAY);

        assertThat(usage.dailyAmount()).isEqualByComparingTo("150.00");
        assertThat(usage.weeklyAmount()).isEqualByComparingTo("1050.00");
        assertThat(usage.dailyCount()).isEqualTo(2);
    }

    @Test
    void returnsZeroUsageForACustomerWithNoLoads() {
        CustomerUsage usage = repository.usage("new-customer", WEDNESDAY);

        assertThat(usage.dailyAmount()).isEqualByComparingTo("0");
        assertThat(usage.weeklyAmount()).isEqualByComparingTo("0");
        assertThat(usage.dailyCount()).isZero();
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
        repository.insert(new LoadAttempt(customerId, id, new BigDecimal(amount), Instant.parse(time), accepted));
    }
}
