package com.dkozel.velocitylimiter.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

// windows are [start, end)
public record UsageWindows(Instant dayStart, Instant dayEnd, Instant weekStart, Instant weekEnd) {

    public static UsageWindows containing(Instant time) {
        LocalDate day = LocalDate.ofInstant(time, ZoneOffset.UTC);
        LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new UsageWindows(
                startOf(day), startOf(day.plusDays(1)),
                startOf(monday), startOf(monday.plusWeeks(1)));
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
