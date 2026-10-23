-- liquibase formatted sql

-- changeset stylemap:20261009-local-admin dbms:postgresql
-- Development-only credentials; do not deploy this seed account to production.
-- Password uses the application's DelegatingPasswordEncoder format ({bcrypt}).
INSERT INTO users (id, username, email, password, role, is_active, created_at)
SELECT '2e2d2cee-81fc-44f8-a44f-84d84bbad289'::uuid,
       'root',
       'root@stylemap.local',
       '{bcrypt}$2a$10$yYef/M4SjAwPHgJTHDYRWOvImV/RNDoZZlZ6XAsuB4T2G7TUEI/a2',
       'ADMIN',
       TRUE,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM users
    WHERE username = 'root' OR email = 'root@stylemap.local'
)
ON CONFLICT DO NOTHING;
