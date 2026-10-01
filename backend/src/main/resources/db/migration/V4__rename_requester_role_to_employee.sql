ALTER TABLE users DROP CONSTRAINT users_role_check;

UPDATE users
SET role = 'EMPLOYEE'
WHERE role = 'REQUESTER';

ALTER TABLE users
    ADD CONSTRAINT users_role_check
    CHECK (role IN ('ADMIN', 'EMPLOYEE'));
