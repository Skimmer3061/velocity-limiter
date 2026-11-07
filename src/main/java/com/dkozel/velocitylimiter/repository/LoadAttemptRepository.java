package com.dkozel.velocitylimiter.repository;

import com.dkozel.velocitylimiter.domain.LoadAttempt;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;

@Repository
public class LoadAttemptRepository {

    private final JdbcClient jdbcClient;

    LoadAttemptRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean exists(String customerId, String id) {
        // not SELECT EXISTS: H2 2.4 caches that result per session, so a row inserted in the same transaction is missed
        return jdbcClient.sql("""
                        SELECT COUNT(*) FROM load_attempt
                        WHERE customer_id = :customerId AND id = :id
                        """)
                .param("customerId", customerId)
                .param("id", id)
                .query(Long.class)
                .single() > 0;
    }

    public void insert(LoadAttempt attempt) {
        jdbcClient.sql("""
                        INSERT INTO load_attempt (customer_id, id, amount, loaded_at, accepted)
                        VALUES (:customerId, :id, :amount, :loadedAt, :accepted)
                        """)
                .param("customerId", attempt.customerId())
                .param("id", attempt.id())
                .param("amount", attempt.amount())
                .param("loadedAt", attempt.loadedAt().atOffset(ZoneOffset.UTC))
                .param("accepted", attempt.accepted())
                .update();
    }
}
