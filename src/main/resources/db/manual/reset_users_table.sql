-- DESTRUCTIVE: this drops all user profiles and authentication/Gmail tokens.
-- Back up the database first. Run manually against the intended local database.
-- This is deliberately not run by the application at startup.

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    first_name VARCHAR(255) NULL,
    last_name VARCHAR(255) NULL,
    name VARCHAR(255) NULL,
    country_code VARCHAR(12) NOT NULL DEFAULT '+91',
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NULL,
    phone VARCHAR(255) NULL,
    gender VARCHAR(20) NULL,
    professional_title VARCHAR(255) NULL,
    location VARCHAR(255) NULL,
    user_experience INT NOT NULL DEFAULT 0,
    github_url VARCHAR(255) NULL,
    linkedin_url VARCHAR(255) NULL,
    portfolio_url VARCHAR(255) NULL,
    other_links LONGTEXT NULL,
    education LONGTEXT NULL,
    skills LONGTEXT NULL,
    experience LONGTEXT NULL,
    projects LONGTEXT NULL,
    certifications LONGTEXT NULL,
    achievements LONGTEXT NULL,
    other_details LONGTEXT NULL,
    work_authorization VARCHAR(100) NULL,
    work_authorization_country VARCHAR(100) NULL,
    visa_sponsorship VARCHAR(100) NULL,
    military_status VARCHAR(100) NULL,
    government_project_experience VARCHAR(20) NULL,
    government_project_details LONGTEXT NULL,
    registration_completed BIT(1) NOT NULL DEFAULT b'0',
    gmail_connected BIT(1) NOT NULL DEFAULT b'0',
    gmail_refresh_token TEXT NULL,
    gmail_email VARCHAR(255) NULL,
    auth_provider VARCHAR(255) NOT NULL DEFAULT 'LOCAL',
    provider_user_id VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_phone (phone),
    UNIQUE KEY uk_users_provider_user_id (provider_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
