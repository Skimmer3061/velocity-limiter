package com.dkozel.velocitylimiter.api;

import com.dkozel.velocitylimiter.domain.LoadCommand;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LoadRequestTest {

    private static final Instant TIME = Instant.parse("2000-01-01T00:00:00Z");

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static final LoadRequest VALID = new LoadRequest("15887", "528", "$3318.47", TIME);

    // the Java property names of the violations, e.g. "customerId" or "timeInSupportedRange"
    private static Set<String> invalidFields(LoadRequest request) {
        return VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void acceptsAValidRequest() {
        assertThat(VALIDATOR.validate(VALID)).isEmpty();
    }

    @Test
    void reportsEveryInvalidFieldAtOnce() {
        assertThat(invalidFields(new LoadRequest(null, null, null, null)))
                .containsExactlyInAnyOrder("id", "customerId", "loadAmount", "time");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void rejectsABlankId(String blank) {
        assertThat(invalidFields(new LoadRequest(blank, blank, "$3318.47", TIME)))
                .containsExactlyInAnyOrder("id", "customerId");
    }

    @Test
    void limitsIdsTo64Characters() {
        String longest = "x".repeat(64);
        String tooLong = "x".repeat(65);

        assertThat(invalidFields(new LoadRequest(longest, longest, "$3318.47", TIME))).isEmpty();
        assertThat(invalidFields(new LoadRequest(tooLong, tooLong, "$3318.47", TIME)))
                .containsExactlyInAnyOrder("id", "customerId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"$0.01", "$0.99", "$10.00", "$3318.47", "$99999999999999999.99"})
    void acceptsAPositiveDollarAmountWithExactlyTwoDecimals(String loadAmount) {
        assertThat(invalidFields(new LoadRequest("1", "c", loadAmount, TIME))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "3318.47", "", "USD 1.00",        // no leading $
            "$0.00", "$-4000.00",             // not positive
            "$00.05", "$01.00",               // leading zero
            "$100", "$1.5",                   // fewer than 2 decimals
            "$1.005", "$5.000",               // more than 2 decimals, even trailing zeros
            "$1E3", "$+5.00", "$1.", "$.50",  // not plain digits
            "$1,000.00", " $1.00",            // separators, surrounding space
            "$100000000000000000.00"          // 18 integer digits: does not fit DECIMAL(19,2)
    })
    void rejectsAnAmountThatIsNotAPositiveDollarAmountWithExactlyTwoDecimals(String loadAmount) {
        assertThat(invalidFields(new LoadRequest("1", "c", loadAmount, TIME))).containsExactly("loadAmount");
    }

    @Test
    void tellsTheClientWhatAnAmountMustLookLike() {
        assertThat(VALIDATOR.validate(new LoadRequest("1", "c", "$1.5", TIME)))
                .singleElement()
                .extracting(ConstraintViolation::getMessage)
                .isEqualTo("must be a positive dollar amount with exactly 2 decimals, like $123.45");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0001-01-01T00:00:00Z", "9999-12-31T23:59:59.999999999Z"})
    void acceptsATimeAtTheEdgesOfTheSupportedRange(Instant time) {
        assertThat(invalidFields(new LoadRequest("1", "c", "$1.00", time))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0000-12-31T23:59:59.999999999Z", "+10000-01-01T00:00:00Z",   // just outside
            "-999999999-01-01T00:00:00Z", "+999999999-12-31T23:59:59Z"    // the extremes Instant can parse
    })
    void rejectsATimeOutsideTheSupportedRange(Instant time) {
        assertThat(invalidFields(new LoadRequest("1", "c", "$1.00", time))).containsExactly("timeInSupportedRange");
    }

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
}
