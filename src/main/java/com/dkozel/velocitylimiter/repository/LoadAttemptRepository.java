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
