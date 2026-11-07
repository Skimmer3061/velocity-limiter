package com.dkozel.velocitylimiter.repository;

import com.dkozel.velocitylimiter.domain.CustomerUsage;
import com.dkozel.velocitylimiter.domain.LoadAttempt;
import com.dkozel.velocitylimiter.domain.UsageWindows;
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

    public CustomerUsage usage(String customerId, UsageWindows windows) {
        return jdbcClient.sql("""
                        SELECT COALESCE(SUM(CASE WHEN loaded_at >= :dayStart AND loaded_at < :dayEnd
                                                 THEN amount END), 0)                             AS daily_amount,
                               COALESCE(SUM(amount), 0)                                           AS weekly_amount,
                               COUNT(CASE WHEN loaded_at >= :dayStart AND loaded_at < :dayEnd
                                          THEN 1 END)                                             AS daily_count
                        FROM load_attempt
                        WHERE customer_id = :customerId
                          AND accepted
                          AND loaded_at >= :weekStart AND loaded_at < :weekEnd
                        """)
                .param("customerId", customerId)
                .param("dayStart", windows.dayStart().atOffset(ZoneOffset.UTC))
                .param("dayEnd", windows.dayEnd().atOffset(ZoneOffset.UTC))
                .param("weekStart", windows.weekStart().atOffset(ZoneOffset.UTC))
                .param("weekEnd", windows.weekEnd().atOffset(ZoneOffset.UTC))
                .query(CustomerUsage.class)
                .single();
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
