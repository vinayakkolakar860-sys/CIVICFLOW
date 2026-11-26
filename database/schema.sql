-- ============================================================================
-- CIVICFLOW — Election Management Suite (CEMS)
-- MySQL 8.x Normalized Relational Database Schema
--
-- NOTICE: Educational Election Simulation Platform.
-- All data stored in this database is for fictional/simulated elections only.
-- Do NOT store real citizen identity records or use for public elections.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS civicflow_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE civicflow_db;

SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 1. roles: System role definitions for RBAC
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS roles (
    role_id     INT AUTO_INCREMENT PRIMARY KEY,
    role_name   VARCHAR(40) NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_roles_name UNIQUE (role_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 2. users: Authenticated application accounts (Super Admin, Election Admin, Voter)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    username              VARCHAR(60) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    role_id               INT NOT NULL,
    email                 VARCHAR(120) NOT NULL,
    full_name             VARCHAR(120) NOT NULL,
    account_status        ENUM('ACTIVE', 'SUSPENDED', 'LOCKED', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until          DATETIME NULL,
    must_change_password  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (role_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_users_role (role_id),
    INDEX idx_users_status (account_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 3. constituencies: Fictional geographic/electoral districts
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS constituencies (
    constituency_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    constituency_code VARCHAR(30) NOT NULL,
    constituency_name VARCHAR(120) NOT NULL,
    city              VARCHAR(80) NOT NULL,
    state             VARCHAR(80) NOT NULL,
    description       TEXT NULL,
    status            ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_constituencies_code UNIQUE (constituency_code),
    INDEX idx_constituencies_status (status),
    INDEX idx_constituencies_city_state (city, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 4. parties: Fictional political parties (demonstration data only)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS parties (
    party_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    party_code  VARCHAR(20) NOT NULL,
    party_name  VARCHAR(120) NOT NULL,
    description TEXT NULL,
    logo_path   VARCHAR(255) NULL,
    status      ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_parties_code UNIQUE (party_code),
    CONSTRAINT uq_parties_name UNIQUE (party_name),
    INDEX idx_parties_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 5. voters: Registered fictional voters (no real government IDs stored)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS voters (
    voter_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    voter_code          VARCHAR(30) NOT NULL,
    user_id             BIGINT NOT NULL,
    first_name          VARCHAR(60) NOT NULL,
    last_name           VARCHAR(60) NOT NULL,
    email               VARCHAR(120) NOT NULL,
    phone_number        VARCHAR(25) NOT NULL,
    date_of_birth       DATE NOT NULL,
    address             VARCHAR(255) NOT NULL,
    city                VARCHAR(80) NOT NULL,
    state               VARCHAR(80) NOT NULL,
    constituency_id     BIGINT NOT NULL,
    registration_date   DATE NOT NULL,
    account_status      ENUM('ACTIVE', 'SUSPENDED', 'LOCKED', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    verification_status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_voters_code UNIQUE (voter_code),
    CONSTRAINT uq_voters_user UNIQUE (user_id),
    CONSTRAINT uq_voters_email UNIQUE (email),
    CONSTRAINT fk_voters_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_voters_constituency FOREIGN KEY (constituency_id) REFERENCES constituencies (constituency_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_voters_constituency (constituency_id),
    INDEX idx_voters_verification (verification_status),
    INDEX idx_voters_account_status (account_status),
    INDEX idx_voters_name (last_name, first_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 6. candidates: Fictional candidates assigned to a party and constituency
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS candidates (
    candidate_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    candidate_code      VARCHAR(30) NOT NULL,
    full_name           VARCHAR(120) NOT NULL,
    profile_description TEXT NULL,
    age                 INT NOT NULL,
    email               VARCHAR(120) NOT NULL,
    contact_number      VARCHAR(25) NOT NULL,
    party_id            BIGINT NOT NULL,
    constituency_id     BIGINT NOT NULL,
    photo_path          VARCHAR(255) NULL,
    status              ENUM('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN') NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_candidates_code UNIQUE (candidate_code),
    CONSTRAINT uq_candidates_email UNIQUE (email),
    CONSTRAINT chk_candidates_age CHECK (age >= 18 AND age <= 120),
    CONSTRAINT fk_candidates_party FOREIGN KEY (party_id) REFERENCES parties (party_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_candidates_constituency FOREIGN KEY (constituency_id) REFERENCES constituencies (constituency_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_candidates_constituency (constituency_id),
    INDEX idx_candidates_party (party_id),
    INDEX idx_candidates_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 7. elections: Simulated election definitions and lifecycle states
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS elections (
    election_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    election_code  VARCHAR(30) NOT NULL,
    election_name  VARCHAR(150) NOT NULL,
    description    TEXT NULL,
    election_type  ENUM('GENERAL', 'LOCAL', 'BY_ELECTION', 'CAMPUS', 'COUNCIL', 'MOCK_POLL') NOT NULL DEFAULT 'GENERAL',
    start_datetime DATETIME NOT NULL,
    end_datetime   DATETIME NOT NULL,
    status         ENUM('DRAFT', 'SCHEDULED', 'OPEN', 'CLOSED', 'PUBLISHED', 'CANCELLED') NOT NULL DEFAULT 'DRAFT',
    created_by     BIGINT NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_elections_code UNIQUE (election_code),
    CONSTRAINT chk_elections_dates CHECK (end_datetime > start_datetime),
    CONSTRAINT fk_elections_created_by FOREIGN KEY (created_by) REFERENCES users (user_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_elections_status (status),
    INDEX idx_elections_dates (start_datetime, end_datetime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 8. election_constituencies: Constituencies participating in an election
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS election_constituencies (
    election_id     BIGINT NOT NULL,
    constituency_id BIGINT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (election_id, constituency_id),
    CONSTRAINT fk_ec_election FOREIGN KEY (election_id) REFERENCES elections (election_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_ec_constituency FOREIGN KEY (constituency_id) REFERENCES constituencies (constituency_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_ec_constituency (constituency_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 9. election_candidates: Approved candidates contesting in an election
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS election_candidates (
    election_candidate_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    election_id           BIGINT NOT NULL,
    candidate_id          BIGINT NOT NULL,
    constituency_id       BIGINT NOT NULL,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ecand_election_candidate UNIQUE (election_id, candidate_id),
    CONSTRAINT uq_ecand_election_const_cand UNIQUE (election_id, constituency_id, candidate_id),
    CONSTRAINT fk_ecand_election FOREIGN KEY (election_id) REFERENCES elections (election_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_ecand_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (candidate_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_ecand_constituency FOREIGN KEY (constituency_id) REFERENCES constituencies (constituency_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_ecand_election_const (election_id, constituency_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 10. voter_eligibility: Explicit voter eligibility mapping per election
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS voter_eligibility (
    eligibility_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    election_id     BIGINT NOT NULL,
    voter_id        BIGINT NOT NULL,
    constituency_id BIGINT NOT NULL,
    is_eligible     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_eligibility_election_voter UNIQUE (election_id, voter_id),
    CONSTRAINT fk_elig_election FOREIGN KEY (election_id) REFERENCES elections (election_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_elig_voter FOREIGN KEY (voter_id) REFERENCES voters (voter_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_elig_constituency FOREIGN KEY (constituency_id) REFERENCES constituencies (constituency_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_elig_election_const (election_id, constituency_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 11. voter_participation: Records THAT a voter participated (NO ballot choice!)
-- Database-enforced UNIQUE(voter_id, election_id) prevents duplicate voting.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS voter_participation (
    participation_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    voter_id             BIGINT NOT NULL,
    election_id          BIGINT NOT NULL,
    submitted_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    participation_status ENUM('SUBMITTED') NOT NULL DEFAULT 'SUBMITTED',
    CONSTRAINT uq_voter_election_participation UNIQUE (voter_id, election_id),
    CONSTRAINT fk_part_voter FOREIGN KEY (voter_id) REFERENCES voters (voter_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_part_election FOREIGN KEY (election_id) REFERENCES elections (election_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_part_election (election_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 12. anonymous_ballots: Records anonymous simulated ballot choices (NO voter_id!)
-- Strictly separated from voter_participation to avoid linking voter -> choice.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS anonymous_ballots (
    ballot_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    election_id     BIGINT NOT NULL,
    candidate_id    BIGINT NOT NULL,
    constituency_id BIGINT NOT NULL,
    submitted_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ballot_election FOREIGN KEY (election_id) REFERENCES elections (election_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_ballot_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (candidate_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_ballot_constituency FOREIGN KEY (constituency_id) REFERENCES constituencies (constituency_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_ballot_election_const (election_id, constituency_id),
    INDEX idx_ballot_election_cand (election_id, candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 13. audit_logs: Immutable security & administrative audit trail
-- Never stores passwords, password hashes, or ballot selections.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    audit_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NULL,
    username         VARCHAR(60) NULL,
    action           VARCHAR(80) NOT NULL,
    module           VARCHAR(60) NOT NULL,
    action_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details          VARCHAR(500) NULL,
    result_status    ENUM('SUCCESS', 'FAILURE', 'DENIED') NOT NULL DEFAULT 'SUCCESS',
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_audit_timestamp (action_timestamp),
    INDEX idx_audit_module (module),
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 14. application_settings: Configurable system parameters
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS application_settings (
    setting_key   VARCHAR(80) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    description   VARCHAR(255) NULL,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

-- Default system roles (idempotent)
INSERT IGNORE INTO roles (role_name, description) VALUES
    ('SUPER_ADMIN', 'Full administrative control over accounts, elections, settings, and audit logs'),
    ('ELECTION_ADMIN', 'Manages elections, candidates, constituencies, voter verifications, and authorized reports'),
    ('VOTER', 'Simulated voter account permitted to view eligible elections, cast simulated ballots, and view published results');

-- Default application settings (idempotent)
INSERT IGNORE INTO application_settings (setting_key, setting_value, description) VALUES
    ('auth.max_failed_attempts', '5', 'Maximum consecutive failed login attempts before temporary lockout'),
    ('auth.lockout_minutes', '15', 'Duration in minutes for temporary account lockout after repeated failures'),
    ('ui.page_size', '25', 'Default number of rows per page in data tables'),
    ('app.demo_banner', 'DEMO DATA — Educational Election Simulation Only', 'Watermark and notice shown across reports and results'),
    ('voting.min_voter_age', '18', 'Minimum simulated voter age in years');
