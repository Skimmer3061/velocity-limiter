package com.dkozel.velocitylimiter.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CustomerRepository {

    private final JdbcClient jdbcClient;

    CustomerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String customerId) {
        // on H2 that already locks the row, but on PostgreSQL we would need both operations
        jdbcClient.sql("MERGE INTO customer KEY (id) VALUES (:id)")
                .param("id", customerId)
                .update();
        jdbcClient.sql("SELECT id FROM customer WHERE id = :id FOR UPDATE")
                .param("id", customerId)
                .query(String.class)
                .single();
    }
}
