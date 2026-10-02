-- liquibase formatted sql

-- changeset VladL:delete-name-constraint
ALTER TABLE hashtags drop constraint uc_hashtags_name;