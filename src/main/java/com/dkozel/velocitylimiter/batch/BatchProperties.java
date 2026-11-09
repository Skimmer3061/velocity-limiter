package com.dkozel.velocitylimiter.batch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("batch")
record BatchProperties(@NotNull @PositiveOrZero Long maxRejectedLines, @NotNull @Positive Integer maxLineLength) {
}
