-- liquibase formatted sql

-- changeset VladL:1774207327025-1
ALTER TABLE users
    ADD is_active BOOLEAN;
ALTER TABLE users
    ADD password VARCHAR(255);
ALTER TABLE users
    ADD role VARCHAR(255);

-- changeset VladL:1774207327025-2
ALTER TABLE users
    ALTER COLUMN is_active SET NOT NULL;

-- changeset VladL:1774207327025-4
ALTER TABLE users
    ALTER COLUMN password SET NOT NULL;

-- changeset VladL:1774207327025-6
ALTER TABLE users
    ALTER COLUMN role SET NOT NULL;

