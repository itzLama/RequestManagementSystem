package com.requestmanagement.backend.automation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs automation only after the triggering change has committed. Each rule runs in its own
 * transaction; any failure is logged and can never roll back the original change.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutomationTriggerListener {
    private final AutomationEngine engine;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTrigger(AutomationTriggerEvent event) {
        try {
            engine.run(event);
        } catch (RuntimeException exception) {
            log.error("Automation failed for {} on task {}.", event.trigger(), event.taskId(), exception);
        }
    }
}
