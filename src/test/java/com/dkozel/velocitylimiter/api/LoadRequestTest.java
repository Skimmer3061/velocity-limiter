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
            "$0.01,    0.01",
            "$99999999999999999.99, 99999999999999999.99"   // the largest amount DECIMAL(19,2) holds
    })
    void parsesTheDollarAmount(String loadAmount, BigDecimal expected) {
        BigDecimal amount = new LoadRequest("1", "c", loadAmount, TIME).toCommand().amount();

        assertThat(amount).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"3318.47", "", "USD 1.00"})
    void rejectsAnAmountWithoutTheDollarSign(String loadAmount) {
        LoadRequest request = new LoadRequest("1", "c", loadAmount, TIME);

        assertThatThrownBy(request::toCommand)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("load_amount must start with '$'");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "$0.00", "$-4000.00",           // not positive
            "$100", "$1.5",                 // fewer than 2 decimals
            "$1.005", "$5.000",             // more than 2 decimals, even trailing zeros
            "$1E3", "$+5.00", "$1.", "$.50", // not plain digits
            "$100000000000000000.00"        // 18 integer digits: does not fit DECIMAL(19,2)
    })
    void rejectsAnAmountThatIsNotPositiveWithExactlyTwoDecimals(String loadAmount) {
        LoadRequest request = new LoadRequest("1", "c", loadAmount, TIME);

        assertThatThrownBy(request::toCommand)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("load_amount must be a positive amount with exactly 2 decimals");
    }
}
