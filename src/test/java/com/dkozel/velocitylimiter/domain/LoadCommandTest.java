package com.dkozel.velocitylimiter.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoadCommandTest {

    private static final Instant TIME = Instant.parse("2000-01-05T10:00:00Z");

    // holds for every caller, not only for loads that came in through LoadRequest
    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-4000.00", "100", "1.5", "1.005"})
    void rejectsAnAmountThatIsNotPositiveWithExactlyTwoDecimals(String amount) {
        assertThatThrownBy(() -> new LoadCommand("c", "1", new BigDecimal(amount), TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be positive with exactly 2 decimals");
    }

    @Test
    void truncatesTheTimeToMicrosecondsSoItStaysInItsDay() {
        // rounded, as the database would, this Sunday load would land on Monday: a new day and a new week
        var command = new LoadCommand("c", "1", new BigDecimal("1.00"), Instant.parse("2000-01-09T23:59:59.9999996Z"));

        assertThat(command.loadedAt()).isEqualTo(Instant.parse("2000-01-09T23:59:59.999999Z"));
    }
}
