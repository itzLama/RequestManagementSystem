package com.requestmanagement.backend.automation;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Marks work performed by a Workflow Automation rule on the current thread.
 * <ul>
 *   <li>Loop prevention: while a rule runs, {@link AutomationTriggerPublisher} publishes nothing, so
 *       changes made by automation never trigger further automation rules.</li>
 *   <li>Attribution: status history and notifications created while a rule runs record the rule
 *       (its names) instead of presenting a person as the author.</li>
 * </ul>
 * The rule's actions run synchronously inside {@link #runAs}, including the commit of their own
 * transaction, so everything they publish sees this context.
 */
public final class AutomationContext {
    private static final ThreadLocal<Rule> CURRENT = new ThreadLocal<>();

    private AutomationContext() { }

    public record Rule(Long id, String name, String nameAr) { }

    public static Optional<Rule> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static <T> T runAs(Rule rule, Supplier<T> work) {
        Rule previous = CURRENT.get();
        CURRENT.set(rule);
        try {
            return work.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
