-- Stage 6: Workflow Optimization.
-- Recurring Task rules (Admin-managed, per Project, built on Task Templates) and simple,
-- predefined Workflow Automation rules. Both are configuration only: every Task they create or
-- change goes through the normal Task services, status history and notifications.

CREATE TABLE project_recurring_task_rules (
    rule_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    frequency VARCHAR(10) NOT NULL,
    -- ISO-8601 day of week (1 = Monday ... 7 = Sunday), weekly rules only.
    weekday SMALLINT,
    -- Day of month (1-31), monthly rules only. 29-31 fall back to the month's last day.
    month_day SMALLINT,
    start_date DATE NOT NULL,
    end_date DATE,
    assignee_id BIGINT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    -- First date the scheduler may create (the day the rule was created or re-activated), so a
    -- new or resumed rule never backfills earlier dates.
    effective_from DATE NOT NULL,
    -- The Administrator who owns the rule (last saved it); created Tasks use them as creator.
    owner_id BIGINT NOT NULL,
    last_run_at TIMESTAMP,
    last_run_result VARCHAR(30),
    last_run_occurrence_date DATE,
    last_run_request_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_recurring_rules_project FOREIGN KEY (project_id)
        REFERENCES projects(project_id) ON DELETE CASCADE,
    -- Template deletion is blocked by the application while a rule uses it; the cascade only
    -- applies when an empty Project is deleted together with its templates.
    CONSTRAINT fk_recurring_rules_template FOREIGN KEY (template_id)
        REFERENCES project_task_templates(template_id) ON DELETE CASCADE,
    CONSTRAINT fk_recurring_rules_assignee FOREIGN KEY (assignee_id)
        REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_recurring_rules_owner FOREIGN KEY (owner_id)
        REFERENCES users(user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_recurring_rules_last_request FOREIGN KEY (last_run_request_id)
        REFERENCES requests(request_id) ON DELETE SET NULL,
    CONSTRAINT recurring_rules_frequency_check CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    CONSTRAINT recurring_rules_weekday_check CHECK (
        (frequency = 'WEEKLY' AND weekday BETWEEN 1 AND 7) OR (frequency <> 'WEEKLY' AND weekday IS NULL)),
    CONSTRAINT recurring_rules_month_day_check CHECK (
        (frequency = 'MONTHLY' AND month_day BETWEEN 1 AND 31) OR (frequency <> 'MONTHLY' AND month_day IS NULL)),
    CONSTRAINT recurring_rules_dates_check CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_recurring_rules_project ON project_recurring_task_rules(project_id);
CREATE INDEX idx_recurring_rules_template ON project_recurring_task_rules(template_id);

-- Duplicate prevention: at most one occurrence per rule and date, even when the job runs twice,
-- overlaps with "Run now", or several backend instances run the scheduler.
CREATE TABLE project_recurring_task_occurrences (
    occurrence_id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    occurrence_date DATE NOT NULL,
    request_id BIGINT,
    manual BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_recurring_occurrences_rule FOREIGN KEY (rule_id)
        REFERENCES project_recurring_task_rules(rule_id) ON DELETE CASCADE,
    CONSTRAINT fk_recurring_occurrences_request FOREIGN KEY (request_id)
        REFERENCES requests(request_id) ON DELETE SET NULL,
    CONSTRAINT uq_recurring_occurrences_rule_date UNIQUE (rule_id, occurrence_date)
);

CREATE INDEX idx_recurring_occurrences_request ON project_recurring_task_occurrences(request_id);

CREATE TABLE project_automation_rules (
    rule_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    rule_name VARCHAR(100) NOT NULL,
    rule_name_ar VARCHAR(100) NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    trigger_status_id BIGINT,
    condition_work_type_id BIGINT,
    condition_priority VARCHAR(20),
    action_assignee_id BIGINT,
    action_status_id BIGINT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT NOT NULL,
    last_run_at TIMESTAMP,
    last_run_result VARCHAR(30),
    last_run_request_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_automation_rules_project FOREIGN KEY (project_id)
        REFERENCES projects(project_id) ON DELETE CASCADE,
    CONSTRAINT fk_automation_rules_trigger_status FOREIGN KEY (trigger_status_id)
        REFERENCES project_status_columns(status_id) ON DELETE CASCADE,
    CONSTRAINT fk_automation_rules_work_type FOREIGN KEY (condition_work_type_id)
        REFERENCES project_work_types(work_type_id) ON DELETE CASCADE,
    CONSTRAINT fk_automation_rules_assignee FOREIGN KEY (action_assignee_id)
        REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_automation_rules_action_status FOREIGN KEY (action_status_id)
        REFERENCES project_status_columns(status_id) ON DELETE SET NULL,
    CONSTRAINT fk_automation_rules_created_by FOREIGN KEY (created_by)
        REFERENCES users(user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_automation_rules_last_request FOREIGN KEY (last_run_request_id)
        REFERENCES requests(request_id) ON DELETE SET NULL,
    CONSTRAINT automation_rules_name_check CHECK (NULLIF(BTRIM(rule_name), '') IS NOT NULL),
    CONSTRAINT automation_rules_name_ar_check CHECK (NULLIF(BTRIM(rule_name_ar), '') IS NOT NULL),
    CONSTRAINT automation_rules_trigger_check CHECK (trigger_type IN ('TASK_CREATED', 'STATUS_CHANGED', 'ASSIGNEE_CHANGED')),
    CONSTRAINT automation_rules_trigger_status_check CHECK (
        (trigger_type = 'STATUS_CHANGED') = (trigger_status_id IS NOT NULL)),
    CONSTRAINT automation_rules_priority_check CHECK (
        condition_priority IS NULL OR condition_priority IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE INDEX idx_automation_rules_project ON project_automation_rules(project_id, trigger_type);

CREATE TABLE project_automation_rule_recipients (
    rule_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (rule_id, user_id),
    CONSTRAINT fk_automation_recipients_rule FOREIGN KEY (rule_id)
        REFERENCES project_automation_rules(rule_id) ON DELETE CASCADE,
    CONSTRAINT fk_automation_recipients_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE
);

-- Changes made by an automation rule record the rule (a snapshot of its names, so history stays
-- readable after the rule is renamed or deleted) instead of presenting a person as the author.
ALTER TABLE status_history ADD COLUMN automation_rule_id BIGINT;
ALTER TABLE status_history ADD COLUMN automation_rule_name VARCHAR(100);
ALTER TABLE status_history ADD COLUMN automation_rule_name_ar VARCHAR(100);
ALTER TABLE status_history ADD CONSTRAINT fk_status_history_automation_rule
    FOREIGN KEY (automation_rule_id) REFERENCES project_automation_rules(rule_id) ON DELETE SET NULL;

ALTER TABLE user_notifications ADD COLUMN automation_rule_id BIGINT;
ALTER TABLE user_notifications ADD COLUMN automation_rule_name VARCHAR(100);
ALTER TABLE user_notifications ADD COLUMN automation_rule_name_ar VARCHAR(100);
ALTER TABLE user_notifications ADD CONSTRAINT fk_user_notifications_automation_rule
    FOREIGN KEY (automation_rule_id) REFERENCES project_automation_rules(rule_id) ON DELETE SET NULL;

ALTER TABLE user_notifications DROP CONSTRAINT user_notifications_type_check;
ALTER TABLE user_notifications ADD CONSTRAINT user_notifications_type_check
    CHECK (notification_type IN ('TASK_ASSIGNED', 'TASK_STATUS_CHANGED', 'TASK_COMMENTED',
                                 'TASK_INTERNAL_NOTE', 'GUEST_MESSAGE', 'AUTOMATION_NOTIFY'));
