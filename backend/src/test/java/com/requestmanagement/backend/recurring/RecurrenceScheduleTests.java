package com.requestmanagement.backend.recurring;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecurrenceScheduleTests {
    private static LocalDate d(String iso) { return LocalDate.parse(iso); }

    @Test
    void dailyRunsEveryDayWithinStartAndEnd() {
        var schedule = new RecurrenceSchedule(RecurrenceSchedule.Frequency.DAILY, null, null, d("2026-11-10"), d("2026-11-20"));
        assertThat(schedule.occursOn(d("2026-11-09"))).isFalse();
        assertThat(schedule.occursOn(d("2026-11-10"))).isTrue();
        assertThat(schedule.occursOn(d("2026-11-20"))).isTrue();
        assertThat(schedule.occursOn(d("2026-11-21"))).isFalse();
    }

    @Test
    void weeklyRunsOnTheChosenIsoWeekday() {
        // 2026-11-14 is a Saturday (ISO 6); Sunday is ISO 7.
        var sunday = new RecurrenceSchedule(RecurrenceSchedule.Frequency.WEEKLY, 7, null, d("2026-11-01"), null);
        assertThat(sunday.occursOn(d("2026-11-15"))).isTrue();
        assertThat(sunday.occursOn(d("2026-11-14"))).isFalse();
        assertThat(sunday.latestOnOrBefore(d("2026-11-14"))).contains(d("2026-11-08"));
        assertThat(sunday.nextOnOrAfter(d("2026-11-14"))).contains(d("2026-11-15"));
        assertThat(sunday.nextOnOrAfter(d("2026-11-15"))).contains(d("2026-11-15"));
    }

    @Test
    void monthlyDay31FallsBackToTheLastDayOfShortMonths() {
        var schedule = new RecurrenceSchedule(RecurrenceSchedule.Frequency.MONTHLY, null, 31, d("2026-01-01"), null);
        assertThat(schedule.occursOn(d("2026-01-31"))).isTrue();
        assertThat(schedule.occursOn(d("2026-02-28"))).isTrue();
        assertThat(schedule.occursOn(d("2026-04-30"))).isTrue();
        assertThat(schedule.occursOn(d("2026-04-29"))).isFalse();
        assertThat(schedule.nextOnOrAfter(d("2026-02-01"))).contains(d("2026-02-28"));
        assertThat(schedule.latestOnOrBefore(d("2026-03-30"))).contains(d("2026-02-28"));
        // Leap year: 29 February.
        var leap = new RecurrenceSchedule(RecurrenceSchedule.Frequency.MONTHLY, null, 30, d("2028-01-01"), null);
        assertThat(leap.nextOnOrAfter(d("2028-02-01"))).contains(d("2028-02-29"));
        assertThat(leap.occursOn(d("2028-02-28"))).isFalse();
    }

    @Test
    void endDateStopsTheScheduleAndLatestIsCappedByIt() {
        var schedule = new RecurrenceSchedule(RecurrenceSchedule.Frequency.WEEKLY, 1, null, d("2026-11-01"), d("2026-11-20"));
        // Mondays in range: 2, 9, 16 November.
        assertThat(schedule.nextOnOrAfter(d("2026-11-17"))).isEmpty();
        assertThat(schedule.latestOnOrBefore(d("2026-12-31"))).contains(d("2026-11-16"));
        assertThat(schedule.latestOnOrBefore(d("2026-11-01"))).isEmpty();
    }

    @Test
    void latestOnOrBeforeReturnsOnlyTheSingleMostRecentMissedDate() {
        var daily = new RecurrenceSchedule(RecurrenceSchedule.Frequency.DAILY, null, null, d("2026-01-01"), null);
        assertThat(daily.latestOnOrBefore(d("2026-11-14"))).contains(d("2026-11-14"));
        var monthly = new RecurrenceSchedule(RecurrenceSchedule.Frequency.MONTHLY, null, 1, d("2026-01-01"), null);
        assertThat(monthly.latestOnOrBefore(d("2026-11-14"))).contains(d("2026-11-01"));
    }

    @Test
    void invalidRulesAreRejected() {
        assertThatThrownBy(() -> new RecurrenceSchedule(RecurrenceSchedule.Frequency.WEEKLY, null, null, d("2026-11-01"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecurrenceSchedule(RecurrenceSchedule.Frequency.MONTHLY, null, 32, d("2026-11-01"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecurrenceSchedule(RecurrenceSchedule.Frequency.DAILY, null, null, d("2026-11-10"), d("2026-11-01")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
