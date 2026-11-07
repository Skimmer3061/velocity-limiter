package com.dkozel.velocitylimiter.api;

import com.dkozel.velocitylimiter.domain.LoadCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoadRequestTest {

    private static final Instant TIME = Instant.parse("2000-01-01T00:00:00Z");

    @Test
    void mapsEveryFieldToTheCommand() {
        LoadCommand command = new LoadRequest("15887", "528", "$3318.47", TIME).toCommand();

        assertThat(command).isEqualTo(new LoadCommand("528", "15887", new BigDecimal("3318.47"), TIME));
    }

    @ParameterizedTest
    @CsvSource({
            "$3318.47, 3318.47",
            "$100,     100.00",
            "$0.01,    0.01"
    })
    void parsesTheDollarAmount(String loadAmount, BigDecimal expected) {
        BigDecimal amount = new LoadRequest("1", "c", loadAmount, TIME).toCommand().amount();

        // compareTo, not equals: "$100" has scale 0, the database returns 100.00 (scale 2)
        assertThat(amount).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"3318.47", "", "USD 1.00"})
    void rejectsAnAmountWithoutTheDollarSign(String loadAmount) {
        LoadRequest request = new LoadRequest("1", "c", loadAmount, TIME);

        assertThatThrownBy(request::toCommand)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("load_amount must start with '$'");
    }
}
