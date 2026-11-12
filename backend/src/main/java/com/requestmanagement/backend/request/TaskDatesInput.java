package com.requestmanagement.backend.request;

import java.time.LocalDate;

/** Date-only planning fields. Either value may be null to clear it. */
public record TaskDatesInput(LocalDate startDate, LocalDate dueDate) { }
