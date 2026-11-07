package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.VelocityLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class VelocityLimitsPropertiesIntegrationTest {

    private static final String[] VALID_LIMITS = {
            "velocity.limits.daily-amount=5000.00",
            "velocity.limits.weekly-amount=20000.00",
            "velocity.limits.daily-count=3"
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    void bindsTheLimits() {
        runner.withPropertyValues(VALID_LIMITS)
                .run(context -> {
                    VelocityLimits limits = context.getBean(VelocityLimitsProperties.class).toLimits();
                    assertThat(limits.dailyAmount()).isEqualByComparingTo("5000");
                    assertThat(limits.weeklyAmount()).isEqualByComparingTo("20000");
                    assertThat(limits.dailyCount()).isEqualTo(3);
                });
    }

    @ParameterizedTest
    @CsvSource({"daily-amount, dailyAmount", "weekly-amount, weeklyAmount", "daily-count, dailyCount"})
    void failsAtStartupWhenALimitIsMissing(String property, String field) {
        runner.withPropertyValues(validLimitsWithout(property))
                .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining(field));
    }

    @ParameterizedTest
    @CsvSource({"daily-amount, dailyAmount", "weekly-amount, weeklyAmount", "daily-count, dailyCount"})
    void failsAtStartupWhenALimitIsZero(String property, String field) {
        runner.withPropertyValues(validLimitsWithout(property))
                .withPropertyValues("velocity.limits." + property + "=0")
                .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining(field));
    }

    private static String[] validLimitsWithout(String property) {
        return Stream.of(VALID_LIMITS)
                .filter(limit -> !limit.startsWith("velocity.limits." + property + "="))
                .toArray(String[]::new);
    }

    @EnableConfigurationProperties(VelocityLimitsProperties.class)
    static class Config {
    }
}
