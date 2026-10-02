-- liquibase formatted sql

-- changeset VladL:fix-db-2
alter table users alter column external_auth_id drop not null;