package com.dkozel.velocitylimiter.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

// a UTC calendar period a limit applies to; a new one (e.g. MONTH) is one constant plus its two switch cases
public enum Period {
    DAY,
    WEEK;

    // windows are [start, end)
    public record Window(Instant start, Instant end) {
    }

    public Window windowContaining(Instant time) {
        LocalDate day = LocalDate.ofInstant(time, ZoneOffset.UTC);
        LocalDate start = switch (this) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        };
        LocalDate end = switch (this) {
            case DAY -> start.plusDays(1);
            case WEEK -> start.plusWeeks(1);
        };
        return new Window(startOf(start), startOf(end));
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
