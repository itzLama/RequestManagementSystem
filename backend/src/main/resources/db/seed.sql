-- Sample Users
INSERT INTO users (full_name, email, password_hash, role)
VALUES
('Sara Saad', 'sara.saad@example.com', 'Password123', 'ADMIN'),
('Nora Ahmed', 'nora.ahmed@example.com', 'Password123', 'REQUESTER'),
('Nouf Khaled', 'nouf.khaled@example.com', 'Password123', 'ADMIN');


-- Request Types
INSERT INTO request_types (type_name, description)
VALUES
('IT & Technical Support', 'Technical issues related to devices, software, and systems.'),
('Access & Permissions', 'Requests related to system access and permissions.'),
('Facilities & Maintenance', 'Requests related to office facilities and maintenance.'),
('Employee Services', 'Requests related to employee services.'),
('Administrative & General Services', 'General administrative service requests.'),
('Other', 'Requests that do not belong to the predefined categories.');


-- Sample Requests
INSERT INTO requests
(title, description, type_id, priority, status, created_by, assigned_to)
VALUES
(
    'Laptop Issue',
    'The laptop is unable to connect to the office Wi-Fi.',
    (SELECT type_id FROM request_types WHERE type_name = 'IT & Technical Support'),
    'MEDIUM',
    'IN_PROGRESS',
    (SELECT user_id FROM users WHERE email = 'nora.ahmed@example.com'),
    (SELECT user_id FROM users WHERE email = 'nouf.khaled@example.com')
),
(
    'System Access',
    'Access is required for the internal system.',
    (SELECT type_id FROM request_types WHERE type_name = 'Access & Permissions'),
    'HIGH',
    'NEW',
    (SELECT user_id FROM users WHERE email = 'nora.ahmed@example.com'),
    NULL
),
(
    'Office Maintenance',
    'Maintenance is required for an office facility.',
    (SELECT type_id FROM request_types WHERE type_name = 'Facilities & Maintenance'),
    'LOW',
    'WAITING_USER',
    (SELECT user_id FROM users WHERE email = 'nora.ahmed@example.com'),
    (SELECT user_id FROM users WHERE email = 'sara.saad@example.com')
);


-- Sample Comments
INSERT INTO comments (request_id, user_id, comment_text, is_internal)
VALUES
(
    (SELECT request_id FROM requests WHERE title = 'Laptop Issue'),
    (SELECT user_id FROM users WHERE email = 'sara.saad@example.com'),
    'When did the issue start?',
    FALSE
),
(
    (SELECT request_id FROM requests WHERE title = 'Laptop Issue'),
    (SELECT user_id FROM users WHERE email = 'nora.ahmed@example.com'),
    'It started this morning when I tried to connect to the office Wi-Fi.',
    FALSE
);


-- Sample Status History
INSERT INTO status_history
(request_id, old_status, new_status, changed_by, change_note)
VALUES
(
    (SELECT request_id FROM requests WHERE title = 'Laptop Issue'),
    'NEW',
    'IN_PROGRESS',
    (SELECT user_id FROM users WHERE email = 'sara.saad@example.com'),
    'Request reviewed and assigned.'
);