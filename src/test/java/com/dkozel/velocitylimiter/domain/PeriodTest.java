package com.dkozel.velocitylimiter.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodTest {

    // 2000-01-03 is a Monday; 2000-01-09 is the Sunday of that week

    @Test
    void midweekLoadUsesItsUtcDayAndItsMondayToMondayWeek() {
        Instant wednesday = Instant.parse("2000-01-05T12:00:00Z");

        assertThat(Period.DAY.windowContaining(wednesday)).isEqualTo(window("2000-01-05", "2000-01-06"));
        assertThat(Period.WEEK.windowContaining(wednesday)).isEqualTo(window("2000-01-03", "2000-01-10"));
    }

    @Test
    void lastSecondOfSundayStillBelongsToTheEndingWeek() {
        Instant sunday = Instant.parse("2000-01-09T23:59:59Z");

        assertThat(Period.DAY.windowContaining(sunday)).isEqualTo(window("2000-01-09", "2000-01-10"));
        assertThat(Period.WEEK.windowContaining(sunday)).isEqualTo(window("2000-01-03", "2000-01-10"));
    }

    @Test
    void midnightOnMondayStartsANewDayAndANewWeek() {
        Instant monday = Instant.parse("2000-01-10T00:00:00Z");

        assertThat(Period.DAY.windowContaining(monday)).isEqualTo(window("2000-01-10", "2000-01-11"));
        assertThat(Period.WEEK.windowContaining(monday)).isEqualTo(window("2000-01-10", "2000-01-17"));
    }

    @Test
    void aWeekCanStartInThePreviousYear() {
        // Friday 2021-01-01 is in ISO week 53 of 2020, which starts on Monday 2020-12-28
        assertThat(Period.WEEK.windowContaining(Instant.parse("2021-01-01T12:00:00Z")))
                .isEqualTo(window("2020-12-28", "2021-01-04"));
    }

    private static Period.Window window(String startDay, String endDay) {
        return new Period.Window(Instant.parse(startDay + "T00:00:00Z"), Instant.parse(endDay + "T00:00:00Z"));
    }
}
