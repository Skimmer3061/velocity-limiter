package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.VelocityLimits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties("velocity.limits")
record VelocityLimitsProperties(
        @NotNull @Positive BigDecimal dailyAmount,
        @NotNull @Positive BigDecimal weeklyAmount,
        @NotNull @Positive Integer dailyCount) {

    VelocityLimits toLimits() {
        return new VelocityLimits(dailyAmount, weeklyAmount, dailyCount);
    }
}
