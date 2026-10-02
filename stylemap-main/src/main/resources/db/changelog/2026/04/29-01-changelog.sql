-- liquibase formatted sql

-- changeset VladL:1777477748386-1
ALTER TABLE post_media
    ADD file_name VARCHAR(255);
ALTER TABLE post_media
    ADD mime_type VARCHAR(100);
ALTER TABLE post_media
    ADD stored_file_path VARCHAR(500);

-- changeset VladL:1777477748386-2
ALTER TABLE post_media
    ALTER COLUMN file_name SET NOT NULL;

-- changeset VladL:1777477748386-4
ALTER TABLE post_media
    ALTER COLUMN mime_type SET NOT NULL;

-- changeset VladL:1777477748386-6
ALTER TABLE post_media
    ALTER COLUMN stored_file_path SET NOT NULL;

-- changeset VladL:1777477748386-8
ALTER TABLE post_media
    DROP COLUMN media_url;

