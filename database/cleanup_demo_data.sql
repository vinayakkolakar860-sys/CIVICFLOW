-- ============================================================================
-- CIVICFLOW — Election Management Suite (CEMS)
-- Cleanup Script: Removes all transactional & demo data while preserving schema,
-- roles, and default application_settings.
--
-- !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
-- WARNING: DESTRUCTIVE ACTION
-- This script truncates all election, voter, candidate, party, and user records.
-- DO NOT EXECUTE AGAINST A PRODUCTION OR LIVE ACTIVE DATABASE WITHOUT EXPLICIT
-- AUTHORIZATION AND A VERIFIED BACKUP.
-- Normal deployment and migrations must preserve existing valid user data.
-- !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
-- ============================================================================

USE civicflow_db;

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE anonymous_ballots;
TRUNCATE TABLE voter_participation;
TRUNCATE TABLE voter_eligibility;
TRUNCATE TABLE election_candidates;
TRUNCATE TABLE election_constituencies;
TRUNCATE TABLE elections;
TRUNCATE TABLE candidates;
TRUNCATE TABLE voters;
TRUNCATE TABLE parties;
TRUNCATE TABLE constituencies;
TRUNCATE TABLE audit_logs;
TRUNCATE TABLE users;

SET FOREIGN_KEY_CHECKS = 1;

-- Ensure core roles and default settings remain present
INSERT IGNORE INTO roles (role_name, description) VALUES
    ('SUPER_ADMIN', 'Full administrative control over accounts, elections, settings, and audit logs'),
    ('ELECTION_ADMIN', 'Manages elections, candidates, constituencies, voter verifications, and authorized reports'),
    ('VOTER', 'Simulated voter account permitted to view eligible elections, cast simulated ballots, and view published results');

INSERT IGNORE INTO application_settings (setting_key, setting_value, description) VALUES
    ('auth.max_failed_attempts', '5', 'Maximum consecutive failed login attempts before temporary lockout'),
    ('auth.lockout_minutes', '15', 'Duration in minutes for temporary account lockout after repeated failures'),
    ('ui.page_size', '25', 'Default number of rows per page in data tables'),
    ('app.demo_banner', 'DEMO DATA — Educational Election Simulation Only', 'Watermark and notice shown across reports and results'),
    ('voting.min_voter_age', '18', 'Minimum simulated voter age in years');
