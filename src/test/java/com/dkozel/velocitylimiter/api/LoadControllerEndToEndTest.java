package com.dkozel.velocitylimiter.api;

import com.dkozel.velocitylimiter.domain.LoadAttempt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoadControllerEndToEndTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void acceptsLoadAndStoresAttempt() {
        var response = mvc.post().uri("/loads")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {"id":"1","customer_id":"e2e-customer","load_amount":"$3318.47","time":"2000-01-01T00:00:00Z"}
                """);

        assertThat(response).hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                {"id":"1","customer_id":"e2e-customer","accepted":true}
                """);

        LoadAttempt stored = jdbcClient.sql("""
                SELECT customer_id, id, amount, loaded_at, accepted FROM load_attempt
                WHERE customer_id = :customerId
                """)
                .param("customerId", "e2e-customer")
                .query(LoadAttempt.class)
                .single();

        assertThat(stored).isEqualTo(new LoadAttempt(
                "e2e-customer", "1", new BigDecimal("3318.47"), Instant.parse("2000-01-01T00:00:00Z"), true));
    }

    @Test
    void rejectsAnInvalidLoadWithoutStoringIt() {
        var response = mvc.post().uri("/loads")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
            {"id":"1","customer_id":"e2e-invalid","load_amount":"$0.00","time":"2000-01-01T00:00:00Z"}
            """);

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM load_attempt WHERE customer_id = :customerId")
                .param("customerId", "e2e-invalid")
                .query(Long.class)
                .single()).isZero();
    }

    @Test
    void listsEveryInvalidFieldByTheNameTheClientSent() {
        var response = mvc.post().uri("/loads")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {"id":"","customer_id":"e2e-invalid","load_amount":"$0.00","time":"2000-01-01T00:00:00Z"}
                """);

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().isLenientlyEqualTo("""
                {"status": 400, "title": "Bad Request", "errors": [
                  {"pointer": "#/id", "detail": "must not be blank"},
                  {"pointer": "#/load_amount",
                   "detail": "must be a positive dollar amount with exactly 2 decimals, like $123.45"}
                ]}
                """);
    }
}
