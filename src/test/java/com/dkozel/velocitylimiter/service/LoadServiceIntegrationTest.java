package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.LoadCommand;
import com.dkozel.velocitylimiter.domain.LoadDecision;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class LoadServiceIntegrationTest {

    private static final Instant TIME = Instant.parse("2000-01-01T00:00:00Z");

    @Autowired
    LoadService service;

    @Test
    void ignoresARepeatedIdForTheSameCustomer() {
        service.process(load("dup-customer", "1", TIME));

        assertThat(service.process(load("dup-customer", "1", TIME.plusSeconds(1)))).isEmpty();
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
}
