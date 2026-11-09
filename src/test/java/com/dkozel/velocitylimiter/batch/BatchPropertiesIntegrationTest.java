package com.dkozel.velocitylimiter.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class BatchPropertiesIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues("batch.max-rejected-lines=100", "batch.max-line-length=10000");

    @ParameterizedTest
    @ValueSource(longs = {0, 100})
    void bindsTheLimit(long limit) {
        // 0 is allowed: a strict run that stops at the first bad line
        runner.withPropertyValues("batch.max-rejected-lines=" + limit)
                .run(context -> assertThat(context.getBean(BatchProperties.class).maxRejectedLines()).isEqualTo(limit));
    }

    @Test
    void bindsTheLineLength() {
        runner.run(context -> assertThat(context.getBean(BatchProperties.class).maxLineLength()).isEqualTo(10_000));
    }

    @ParameterizedTest
    @CsvSource({"maxRejectedLines, max-line-length=10000", "maxLineLength, max-rejected-lines=100"})
    void failsAtStartupWhenALimitIsMissing(String field, String onlyOtherLimit) {
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues("batch." + onlyOtherLimit)
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining(field).hasMessageContaining("must not be null"));
    }

    @Test
    void failsAtStartupWhenTheLimitIsNegative() {
        runner.withPropertyValues("batch.max-rejected-lines=-1")
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("maxRejectedLines").hasMessageContaining("must be greater than or equal to 0"));
    }

    @Test
    void failsAtStartupWhenTheLineLengthIsZero() {
        runner.withPropertyValues("batch.max-line-length=0")
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("maxLineLength").hasMessageContaining("must be greater than 0"));
    }

    @EnableConfigurationProperties(BatchProperties.class)
    static class Config {
    }
}
