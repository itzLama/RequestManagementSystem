package com.requestmanagement.backend.recurring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Spring @Scheduled trigger for Recurring Tasks, evaluated in the planning time zone
 * (Asia/Riyadh by default). Runs every 10 minutes by default, so a missed tick or a restart is
 * caught up the same day; duplicates are impossible thanks to the (rule, date) unique key.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "masar.recurring.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class RecurringTaskScheduler {
    private final RecurringTaskRunner runner;

    @Scheduled(cron = "${masar.recurring.cron:0 */10 * * * *}", zone = "${masar.planning.time-zone:Asia/Riyadh}")
    public void run() {
        try {
            int created = runner.runDue();
            if (created > 0) log.info("Recurring tasks: created {} occurrence(s).", created);
        } catch (RuntimeException exception) {
            log.error("Recurring task run failed.", exception);
        }
    }
}
