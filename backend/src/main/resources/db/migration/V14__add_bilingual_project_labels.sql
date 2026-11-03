ALTER TABLE project_work_types ADD COLUMN work_type_name_ar VARCHAR(100);

ALTER TABLE project_guest_fields ADD COLUMN field_label_ar VARCHAR(100);
ALTER TABLE project_guest_field_options ADD COLUMN option_label_ar VARCHAR(100);

ALTER TABLE request_guest_field_answers ADD COLUMN field_label_ar_snapshot VARCHAR(100);
ALTER TABLE request_guest_field_answers ADD COLUMN option_label_ar_snapshot VARCHAR(100);

ALTER TABLE project_work_types
    ADD CONSTRAINT project_work_types_name_ar_check
    CHECK (work_type_name_ar IS NULL OR NULLIF(BTRIM(work_type_name_ar), '') IS NOT NULL);
ALTER TABLE project_guest_fields
    ADD CONSTRAINT project_guest_fields_label_ar_check
    CHECK (field_label_ar IS NULL OR NULLIF(BTRIM(field_label_ar), '') IS NOT NULL);
ALTER TABLE project_guest_field_options
    ADD CONSTRAINT guest_field_options_label_ar_check
    CHECK (option_label_ar IS NULL OR NULLIF(BTRIM(option_label_ar), '') IS NOT NULL);

CREATE UNIQUE INDEX uq_project_work_types_project_name_ar_ci
    ON project_work_types(project_id, LOWER(BTRIM(work_type_name_ar)))
    WHERE work_type_name_ar IS NOT NULL;
CREATE UNIQUE INDEX uq_project_guest_fields_project_label_ar_ci
    ON project_guest_fields(project_id, LOWER(BTRIM(field_label_ar)))
    WHERE field_label_ar IS NOT NULL;
CREATE UNIQUE INDEX uq_guest_field_options_field_label_ar_ci
    ON project_guest_field_options(guest_field_id, LOWER(BTRIM(option_label_ar)))
    WHERE option_label_ar IS NOT NULL;
