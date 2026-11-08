package com.dkozel.velocitylimiter.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LockTimeoutIntegrationTest {

    @Autowired
    JdbcClient jdbcClient;

    // a mistyped property is ignored silently, so read the value back from the database
    @Test
    void appliesTheConfiguredLockTimeout() {
        assertThat(jdbcClient.sql("SELECT LOCK_TIMEOUT()").query(Integer.class).single()).isEqualTo(1000);
    }
}
