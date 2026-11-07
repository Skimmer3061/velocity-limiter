package com.dkozel.velocitylimiter.domain;

import java.math.BigDecimal;

public record CustomerUsage(BigDecimal dailyAmount, BigDecimal weeklyAmount, int dailyCount) {
}
