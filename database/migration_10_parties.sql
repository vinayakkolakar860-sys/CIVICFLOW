-- ============================================================================
-- CIVICFLOW — Election Management Suite (CEMS)
-- Database Migration: 10 Designated Parties & Candidate Rosters
--
-- Adds/updates the 10 parties:
-- 1. BJP
-- 2. INC
-- 3. AAP
-- 4. CJP
-- 5. JDS
-- 6. RJP
-- 7. TMC
-- 8. BSP
-- 9. AIADMK
-- 10. SP
--
-- Idempotent: Can be executed multiple times safely without errors or duplicates.
-- Preserves existing candidate and election relationships.
-- ============================================================================

USE civicflow_db;

SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 1. Upsert the 10 Parties
-- ----------------------------------------------------------------------------
INSERT INTO parties (party_id, party_code, party_name, description, status)
VALUES
    (1,  'BJP',    'BJP',    'Bharatiya Janata Party (Simulation Record)',                'ACTIVE'),
    (2,  'INC',    'INC',    'Indian National Congress (Simulation Record)',              'ACTIVE'),
    (3,  'AAP',    'AAP',    'Aam Aadmi Party (Simulation Record)',                       'ACTIVE'),
    (4,  'CJP',    'CJP',    'Citizen Justice Party (Simulation Record)',                 'ACTIVE'),
    (5,  'JDS',    'JDS',    'Janata Dal (Secular) (Simulation Record)',                  'ACTIVE'),
    (6,  'RJP',    'RJP',    'Rashtriya Janata Party (Simulation Record)',                'ACTIVE'),
    (7,  'TMC',    'TMC',    'All India Trinamool Congress (Simulation Record)',          'ACTIVE'),
    (8,  'BSP',    'BSP',    'Bahujan Samaj Party (Simulation Record)',                   'ACTIVE'),
    (9,  'AIADMK', 'AIADMK', 'All India Anna Dravida Munnetra Kazhagam (Simulation Record)', 'ACTIVE'),
    (10, 'SP',     'SP',     'Samajwadi Party (Simulation Record)',                       'ACTIVE')
ON DUPLICATE KEY UPDATE
    party_code  = VALUES(party_code),
    party_name  = VALUES(party_name),
    description = VALUES(description),
    status      = VALUES(status);

-- ----------------------------------------------------------------------------
-- 2. Ensure candidate assignments exist for all 10 parties
-- Candidates 1-8 already reference party_id 1-4 (BJP, INC, AAP, CJP).
-- Add candidates 9-14 for JDS (5), RJP (6), TMC (7), BSP (8), AIADMK (9), SP (10).
-- ----------------------------------------------------------------------------
SET @cst_nb = (SELECT constituency_id FROM constituencies WHERE constituency_code = 'CST-NB01');
SET @cst_rm = (SELECT constituency_id FROM constituencies WHERE constituency_code = 'CST-RM02');
SET @cst_wv = (SELECT constituency_id FROM constituencies WHERE constituency_code = 'CST-WV03');
SET @cst_eh = (SELECT constituency_id FROM constituencies WHERE constituency_code = 'CST-EH04');

INSERT INTO candidates (candidate_id, candidate_code, full_name, profile_description, age, email, contact_number, party_id, constituency_id, status)
VALUES
    (9,  'CND-2026-009', 'Kavita Rao',     'Simulation candidate focused on educational governance and municipal libraries.', 36, 'kavita.rao@civicflow.demo',     '+1-555-020-0009', 5,  @cst_nb, 'APPROVED'),
    (10, 'CND-2026-010', 'Rajesh Verma',   'Simulation candidate advocating small-enterprise incubators and local job fairs.', 41, 'rajesh.verma@civicflow.demo',   '+1-555-020-0010', 6,  @cst_rm, 'APPROVED'),
    (11, 'CND-2026-011', 'Debashis Sen',   'Simulation candidate prioritizing urban transit access and public park rejuvenation.', 38, 'debashis.sen@civicflow.demo',   '+1-555-020-0011', 7,  @cst_wv, 'APPROVED'),
    (12, 'CND-2026-012', 'Sunil Bharti',   'Simulation candidate emphasizing clean water access and vocational training programs.', 44, 'sunil.bharti@civicflow.demo',   '+1-555-020-0012', 8,  @cst_eh, 'APPROVED'),
    (13, 'CND-2026-013', 'Karthik Selvam', 'Simulation candidate advocating digital public infrastructure and affordable housing.', 39, 'karthik.selvam@civicflow.demo', '+1-555-020-0013', 9,  @cst_nb, 'APPROVED'),
    (14, 'CND-2026-014', 'Anand Pratap',   'Simulation candidate championing youth sports facilities and neighborhood safety.', 34, 'anand.pratap@civicflow.demo',   '+1-555-020-0014', 10, @cst_rm, 'APPROVED')
ON DUPLICATE KEY UPDATE
    full_name           = VALUES(full_name),
    profile_description = VALUES(profile_description),
    party_id            = VALUES(party_id),
    constituency_id     = VALUES(constituency_id),
    status              = VALUES(status);

-- ----------------------------------------------------------------------------
-- 3. Assign new candidates to active simulation elections
-- Election 1: 2026 Spring Civic Council Simulation (OPEN)
-- ----------------------------------------------------------------------------
SET @elc_spring = (SELECT election_id FROM elections WHERE election_code = 'ELC-2026-SPR01');

INSERT INTO election_candidates (election_id, candidate_id, constituency_id)
SELECT @elc_spring, 9,  @cst_nb FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM election_candidates WHERE election_id = @elc_spring AND candidate_id = 9)
UNION ALL
SELECT @elc_spring, 10, @cst_rm FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM election_candidates WHERE election_id = @elc_spring AND candidate_id = 10)
UNION ALL
SELECT @elc_spring, 11, @cst_wv FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM election_candidates WHERE election_id = @elc_spring AND candidate_id = 11)
UNION ALL
SELECT @elc_spring, 12, @cst_eh FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM election_candidates WHERE election_id = @elc_spring AND candidate_id = 12)
UNION ALL
SELECT @elc_spring, 13, @cst_nb FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM election_candidates WHERE election_id = @elc_spring AND candidate_id = 13)
UNION ALL
SELECT @elc_spring, 14, @cst_rm FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM election_candidates WHERE election_id = @elc_spring AND candidate_id = 14);

SET FOREIGN_KEY_CHECKS = 1;
