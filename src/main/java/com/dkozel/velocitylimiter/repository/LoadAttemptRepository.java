package com.dkozel.velocitylimiter.repository;

import com.dkozel.velocitylimiter.domain.LoadAttempt;
import com.dkozel.velocitylimiter.domain.Period;
import com.dkozel.velocitylimiter.domain.Usage;
import com.dkozel.velocitylimiter.domain.Violation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

@Repository
public class LoadAttemptRepository {

    private final JdbcClient jdbcClient;

    LoadAttemptRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<LoadAttempt> find(String customerId, String id) {
        return jdbcClient.sql("""
                        SELECT customer_id, id, amount, loaded_at, accepted FROM load_attempt
                        WHERE customer_id = :customerId AND id = :id
                        """)
                .param("customerId", customerId)
                .param("id", id)
                .query(LoadAttempt.class)
                .optional();
    }

    // accepted loads only: a declined attempt never counts toward a limit
    public Usage usage(String customerId, Period.Window window) {
        return jdbcClient.sql("""
                        SELECT COALESCE(SUM(amount), 0), COUNT(*)
                        FROM load_attempt
                        WHERE customer_id = :customerId
                          AND accepted
                          AND loaded_at >= :start AND loaded_at < :end
                        """)
                .param("customerId", customerId)
                .param("start", window.start().atOffset(ZoneOffset.UTC))
                .param("end", window.end().atOffset(ZoneOffset.UTC))
                .query((row, rowNumber) -> new Usage(row.getBigDecimal(1), row.getLong(2)))
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

    public void insertViolations(String customerId, String loadId, Set<Violation> violations) {
        for (Violation violation : violations) {
            jdbcClient.sql("""
                            INSERT INTO load_attempt_violation (customer_id, load_id, rule)
                            VALUES (:customerId, :loadId, :rule)
                            """)
                    .param("customerId", customerId)
                    .param("loadId", loadId)
                    .param("rule", violation.name())
                    .update();
        }
    }
}
