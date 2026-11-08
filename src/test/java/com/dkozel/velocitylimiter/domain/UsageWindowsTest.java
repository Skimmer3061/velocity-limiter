package com.dkozel.velocitylimiter.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UsageWindowsTest {

    // 2000-01-03 is a Monday; 2000-01-09 is the Sunday of that week

    @Test
    void midweekLoadUsesItsUtcDayAndItsMondayToMondayWeek() {
        assertThat(UsageWindows.containing(Instant.parse("2000-01-05T12:00:00Z"))).isEqualTo(new UsageWindows(
                Instant.parse("2000-01-05T00:00:00Z"), Instant.parse("2000-01-06T00:00:00Z"),
                Instant.parse("2000-01-03T00:00:00Z"), Instant.parse("2000-01-10T00:00:00Z")));
    }

    @Test
    void lastSecondOfSundayStillBelongsToTheEndingWeek() {
        assertThat(UsageWindows.containing(Instant.parse("2000-01-09T23:59:59Z"))).isEqualTo(new UsageWindows(
                Instant.parse("2000-01-09T00:00:00Z"), Instant.parse("2000-01-10T00:00:00Z"),
                Instant.parse("2000-01-03T00:00:00Z"), Instant.parse("2000-01-10T00:00:00Z")));
    }

    @Test
    void midnightOnMondayStartsANewDayAndANewWeek() {
        assertThat(UsageWindows.containing(Instant.parse("2000-01-10T00:00:00Z"))).isEqualTo(new UsageWindows(
                Instant.parse("2000-01-10T00:00:00Z"), Instant.parse("2000-01-11T00:00:00Z"),
                Instant.parse("2000-01-10T00:00:00Z"), Instant.parse("2000-01-17T00:00:00Z")));
    }

    @Test
    void aWeekCanStartInThePreviousYear() {
        // Friday 2021-01-01 is in ISO week 53 of 2020, which starts on Monday 2020-12-28
        assertThat(UsageWindows.containing(Instant.parse("2021-01-01T12:00:00Z"))).isEqualTo(new UsageWindows(
                Instant.parse("2021-01-01T00:00:00Z"), Instant.parse("2021-01-02T00:00:00Z"),
                Instant.parse("2020-12-28T00:00:00Z"), Instant.parse("2021-01-04T00:00:00Z")));
    }
}
