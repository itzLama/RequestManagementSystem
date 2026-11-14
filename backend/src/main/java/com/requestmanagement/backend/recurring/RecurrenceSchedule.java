package com.requestmanagement.backend.recurring;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Pure recurrence date math (no time zones: callers pass dates in the planning zone).
 * Weekly rules use ISO weekdays (1 = Monday ... 7 = Sunday). Monthly rules on days 29-31 fall
 * back to the month's last day (e.g. day 31 runs on 30 April and 28/29 February).
 */
public record RecurrenceSchedule(Frequency frequency, Integer weekday, Integer monthDay,
                                 LocalDate startDate, LocalDate endDate) {
    /** Longest possible gap between two occurrences (monthly: 28 Feb -> 31 Mar). */
    private static final int MAX_GAP_DAYS = 31;

    public enum Frequency { DAILY, WEEKLY, MONTHLY }

    public RecurrenceSchedule {
        if (frequency == null || startDate == null) throw new IllegalArgumentException("Frequency and start date are required.");
        if (frequency == Frequency.WEEKLY && (weekday == null || weekday < 1 || weekday > 7)) {
            throw new IllegalArgumentException("Weekly rules need a weekday (1-7).");
        }
        if (frequency == Frequency.MONTHLY && (monthDay == null || monthDay < 1 || monthDay > 31)) {
            throw new IllegalArgumentException("Monthly rules need a day of month (1-31).");
        }
        if (endDate != null && endDate.isBefore(startDate)) throw new IllegalArgumentException("End date is before start date.");
    }

    public boolean occursOn(LocalDate date) {
        if (date.isBefore(startDate) || (endDate != null && date.isAfter(endDate))) return false;
        return switch (frequency) {
            case DAILY -> true;
            case WEEKLY -> date.getDayOfWeek().getValue() == weekday;
            case MONTHLY -> date.getDayOfMonth() == Math.min(monthDay, date.lengthOfMonth());
        };
    }

    /** The most recent occurrence on or before {@code limit}, if any. */
    public Optional<LocalDate> latestOnOrBefore(LocalDate limit) {
        LocalDate date = endDate != null && endDate.isBefore(limit) ? endDate : limit;
        for (int i = 0; i <= MAX_GAP_DAYS && !date.isBefore(startDate); i++, date = date.minusDays(1)) {
            if (occursOn(date)) return Optional.of(date);
        }
        return Optional.empty();
    }

    /** The first occurrence on or after {@code from}, if the rule has not ended by then. */
    public Optional<LocalDate> nextOnOrAfter(LocalDate from) {
        LocalDate date = from.isBefore(startDate) ? startDate : from;
        for (int i = 0; i <= MAX_GAP_DAYS; i++, date = date.plusDays(1)) {
            if (endDate != null && date.isAfter(endDate)) return Optional.empty();
            if (occursOn(date)) return Optional.of(date);
        }
        return Optional.empty();
    }
}
