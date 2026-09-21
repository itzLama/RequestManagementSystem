UPDATE users
SET password_hash = CASE email
    WHEN 'sara.saad@example.com' THEN '$2a$10$D6GkgpH60O.XvS98mYXDMO8.nuVXrEsRiWw1e0VlgyrvYGUB37X8e'
    WHEN 'nora.ahmed@example.com' THEN '$2a$10$jtk8BdebpSRfr.SO.ZeI.e7bsD2AijpmvUjvpBGpZXK6UUcknUN.i'
    WHEN 'nouf.khaled@example.com' THEN '$2a$10$AmAjJa/72SVmjFq7di391ub81i6KevJsH3QyUHRUwLt6sEEhMgNG6'
END
WHERE (email = 'sara.saad@example.com' AND full_name = 'Sara Saad' AND role = 'ADMIN')
   OR (email = 'nora.ahmed@example.com' AND full_name = 'Nora Ahmed' AND role = 'REQUESTER')
   OR (email = 'nouf.khaled@example.com' AND full_name = 'Nouf Khaled' AND role = 'ADMIN');
