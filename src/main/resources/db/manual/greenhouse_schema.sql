-- Apply once if Hibernate ddl-auto=update is disabled for this database.
ALTER TABLE users
    ADD COLUMN first_name VARCHAR(255) NULL,
    ADD COLUMN last_name VARCHAR(255) NULL,
    ADD COLUMN country_code VARCHAR(12) NOT NULL DEFAULT '+91';

-- Keep older Google-login accounts usable by deriving names from `name`.
UPDATE users
SET first_name = CASE
        WHEN name IS NULL OR LOCATE(' ', TRIM(name)) = 0 THEN TRIM(name)
        ELSE TRIM(LEFT(TRIM(name), CHAR_LENGTH(TRIM(name)) - CHAR_LENGTH(SUBSTRING_INDEX(TRIM(name), ' ', -1)) - 1))
    END,
    last_name = CASE
        WHEN name IS NULL OR LOCATE(' ', TRIM(name)) = 0 THEN ''
        ELSE TRIM(SUBSTRING_INDEX(TRIM(name), ' ', -1))
    END
WHERE first_name IS NULL OR first_name = '';

ALTER TABLE job_applications
    ADD COLUMN cover_letter_path VARCHAR(1000) NULL,
    ADD COLUMN cover_letter_body LONGTEXT NULL;

-- HR contact metadata used to personalize the job inquiry campaign.
ALTER TABLE hr_contacts
    ADD COLUMN hr_name VARCHAR(255) NULL,
    ADD COLUMN designation VARCHAR(500) NULL;
