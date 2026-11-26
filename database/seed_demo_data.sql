-- ============================================================================
-- CIVICFLOW — Election Management Suite (CEMS)
-- Repeatable Fictional Demonstration Data Seed Script
--
-- NOTICE:
-- All users, voters, candidates, parties, constituencies, and elections below
-- are 100% FICTIONAL demonstration data for local educational evaluation only.
--
-- LOCAL DEMONSTRATION CREDENTIALS (BCrypt hashed, cost=10):
-- All demo accounts are provisioned with secure salted BCrypt hashes and forced rotation on first login (must_change_password=TRUE).
-- Accounts: superadmin (SUPER_ADMIN), electionadmin (ELECTION_ADMIN), voter.maya ... voter.elena (VOTER).
--
-- !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
-- WARNING: DESTRUCTIVE ACTION
-- This script resets and truncates existing table records.
-- DO NOT EXECUTE AGAINST A PRODUCTION OR LIVE ACTIVE DATABASE WITHOUT EXPLICIT
-- AUTHORIZATION AND A VERIFIED BACKUP.
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

-- Ensure Roles & Settings exist
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

-- ----------------------------------------------------------------------------
-- 1. Seed Users (Administrators & Fictional Voters)
-- ----------------------------------------------------------------------------
SET @role_super = (SELECT role_id FROM roles WHERE role_name = 'SUPER_ADMIN');
SET @role_eladmin = (SELECT role_id FROM roles WHERE role_name = 'ELECTION_ADMIN');
SET @role_voter = (SELECT role_id FROM roles WHERE role_name = 'VOTER');

INSERT INTO users (username, password_hash, role_id, email, full_name, account_status, must_change_password) VALUES
    ('superadmin',    '$2a$10$ycHGftsm24WYMEYxzeHzJuezdCQkWItHq21xbXZO5ix2o9C26CICS', @role_super,   'superadmin@civicflow.demo',    'Jordan Vance (Demo Super Admin)',     'ACTIVE', FALSE),
    ('electionadmin', '$2a$10$ycHGftsm24WYMEYxzeHzJuezdCQkWItHq21xbXZO5ix2o9C26CICS', @role_eladmin, 'electionadmin@civicflow.demo', 'Claire Mercer (Demo Election Admin)', 'ACTIVE', FALSE),
    ('voter.maya',    '$2a$10$ycHGftsm24WYMEYxzeHzJuezdCQkWItHq21xbXZO5ix2o9C26CICS', @role_voter,   'maya.linwood@civicflow.demo',  'Maya Linwood',                        'ACTIVE', FALSE),
    ('voter.liam',    '$2a$10$ycHGftsm24WYMEYxzeHzJuezdCQkWItHq21xbXZO5ix2o9C26CICS', @role_voter,   'liam.harper@civicflow.demo',   'Liam Harper',                         'ACTIVE', FALSE),
    ('voter.priya',   '$2a$10$ycHGftsm24WYMEYxzeHzJuezdCQkWItHq21xbXZO5ix2o9C26CICS', @role_voter,   'priya.nambiar@civicflow.demo', 'Priya Nambiar',                       'ACTIVE', FALSE),
    ('voter.daniel',  '$2a$10$ycHGftsm24WYMEYxzeHzJuezdCQkWItHq21xbXZO5ix2o9C26CICS', @role_voter,   'daniel.okafor@civicflow.demo', 'Daniel Okafor',                       'ACTIVE', FALSE),
    ('voter.sofia',   '$2a$10$ycHGftsm24WYMEYxzeHzJuezdCQkWItHq21xbXZO5ix2o9C26CICS', @role_voter,   'sofia.alvarez@civicflow.demo', 'Sofia Alvarez',                       'ACTIVE', FALSE),
    ('voter.noah',    '$2a$10$ycHGftsm24WYMEYxzeHzJuezdCQkWItHq21xbXZO5ix2o9C26CICS', @role_voter,   'noah.takahashi@civicflow.demo','Noah Takahashi',                      'ACTIVE', FALSE),
    ('voter.elena',   '$2a$10$ycHGftsm24WYMEYxzeHzJuezdCQkWItHq21xbXZO5ix2o9C26CICS', @role_voter,   'elena.kowalski@civicflow.demo','Elena Kowalski',                      'ACTIVE', FALSE);

SET @uid_super  = (SELECT user_id FROM users WHERE username = 'superadmin');
SET @uid_maya   = (SELECT user_id FROM users WHERE username = 'voter.maya');
SET @uid_liam   = (SELECT user_id FROM users WHERE username = 'voter.liam');
SET @uid_priya  = (SELECT user_id FROM users WHERE username = 'voter.priya');
SET @uid_daniel = (SELECT user_id FROM users WHERE username = 'voter.daniel');
SET @uid_sofia  = (SELECT user_id FROM users WHERE username = 'voter.sofia');
SET @uid_noah   = (SELECT user_id FROM users WHERE username = 'voter.noah');
SET @uid_elena  = (SELECT user_id FROM users WHERE username = 'voter.elena');

-- ----------------------------------------------------------------------------
-- 2. Seed Fictional Constituencies
-- ----------------------------------------------------------------------------
INSERT INTO constituencies (constituency_code, constituency_name, city, state, description, status) VALUES
    ('CST-NB01', 'Northbrook District', 'Oakridge',   'Meridian State', 'Fictional northern residential and university district.', 'ACTIVE'),
    ('CST-RM02', 'Riverdale Metro',     'Riverdale',  'Meridian State', 'Fictional metropolitan transit and commerce hub.',       'ACTIVE'),
    ('CST-WV03', 'Westgate Valley',     'Westgate',   'Solis State',    'Fictional valley innovation and agricultural zone.',     'ACTIVE'),
    ('CST-EH04', 'East Harbor Ward',    'Harborview', 'Solis State',    'Fictional coastal community and port district.',         'ACTIVE');

SET @cst_nb = (SELECT constituency_id FROM constituencies WHERE constituency_code = 'CST-NB01');
SET @cst_rm = (SELECT constituency_id FROM constituencies WHERE constituency_code = 'CST-RM02');
SET @cst_wv = (SELECT constituency_id FROM constituencies WHERE constituency_code = 'CST-WV03');
SET @cst_eh = (SELECT constituency_id FROM constituencies WHERE constituency_code = 'CST-EH04');

-- ----------------------------------------------------------------------------
-- 3. Seed Fictional Parties (10 Standard Simulation Parties)
-- ----------------------------------------------------------------------------
INSERT INTO parties (party_id, party_code, party_name, description, status) VALUES
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

SET @pty_bjp    = 1;
SET @pty_inc    = 2;
SET @pty_aap    = 3;
SET @pty_cjp    = 4;
SET @pty_jds    = 5;
SET @pty_rjp    = 6;
SET @pty_tmc    = 7;
SET @pty_bsp    = 8;
SET @pty_aiadmk = 9;
SET @pty_sp     = 10;

-- ----------------------------------------------------------------------------
-- 4. Seed Fictional Voters
-- ----------------------------------------------------------------------------
INSERT INTO voters (voter_code, user_id, first_name, last_name, email, phone_number, date_of_birth, address, city, state, constituency_id, registration_date, account_status, verification_status) VALUES
    ('VTR-2026-1001', @uid_maya,   'Maya',   'Linwood',   'maya.linwood@civicflow.demo',   '+1-555-010-1001', '1996-04-12', '104 Cedar Lane',     'Oakridge',  'Meridian State', @cst_nb, CURDATE() - INTERVAL 30 DAY, 'ACTIVE', 'APPROVED'),
    ('VTR-2026-1002', @uid_liam,   'Liam',   'Harper',    'liam.harper@civicflow.demo',    '+1-555-010-1002', '1991-09-23', '218 Maple Terrace',  'Oakridge',  'Meridian State', @cst_nb, CURDATE() - INTERVAL 28 DAY, 'ACTIVE', 'APPROVED'),
    ('VTR-2026-1003', @uid_priya,  'Priya',  'Nambiar',   'priya.nambiar@civicflow.demo',  '+1-555-010-1003', '1994-11-05', '45 Riverfront Blvd', 'Riverdale', 'Meridian State', @cst_rm, CURDATE() - INTERVAL 25 DAY, 'ACTIVE', 'APPROVED'),
    ('VTR-2026-1004', @uid_daniel, 'Daniel', 'Okafor',    'daniel.okafor@civicflow.demo',  '+1-555-010-1004', '1988-02-18', '710 Harbor Road',    'Riverdale', 'Meridian State', @cst_rm, CURDATE() - INTERVAL 22 DAY, 'ACTIVE', 'APPROVED'),
    ('VTR-2026-1005', @uid_sofia,  'Sofia',  'Alvarez',   'sofia.alvarez@civicflow.demo',  '+1-555-010-1005', '1999-07-30', '88 Meadow View Dr',  'Westgate',  'Solis State',    @cst_wv, CURDATE() - INTERVAL 18 DAY, 'ACTIVE', 'APPROVED'),
    ('VTR-2026-1006', @uid_noah,   'Noah',   'Takahashi', 'noah.takahashi@civicflow.demo', '+1-555-010-1006', '2001-01-14', '330 Birch Street',   'Oakridge',  'Meridian State', @cst_nb, CURDATE() - INTERVAL 5 DAY,  'ACTIVE', 'PENDING'),
    ('VTR-2026-1007', @uid_elena,  'Elena',  'Kowalski',  'elena.kowalski@civicflow.demo', '+1-555-010-1007', '1997-08-09', '19 Summit Avenue',   'Westgate',  'Solis State',    @cst_wv, CURDATE() - INTERVAL 2 DAY,  'ACTIVE', 'PENDING');

SET @vtr_maya   = (SELECT voter_id FROM voters WHERE voter_code = 'VTR-2026-1001');
SET @vtr_liam   = (SELECT voter_id FROM voters WHERE voter_code = 'VTR-2026-1002');
SET @vtr_priya  = (SELECT voter_id FROM voters WHERE voter_code = 'VTR-2026-1003');
SET @vtr_daniel = (SELECT voter_id FROM voters WHERE voter_code = 'VTR-2026-1004');
SET @vtr_sofia  = (SELECT voter_id FROM voters WHERE voter_code = 'VTR-2026-1005');

-- ----------------------------------------------------------------------------
-- 5. Seed Fictional Candidates (All 10 Parties)
-- ----------------------------------------------------------------------------
INSERT INTO candidates (candidate_id, candidate_code, full_name, profile_description, age, email, contact_number, party_id, constituency_id, status) VALUES
    (1,  'CND-2026-001', 'Avery Chen',      'Fictional urban planner advocating smart public transit and open civic budgets.',      37, 'avery.chen@civicflow.demo',      '+1-555-020-0001', @pty_bjp,    @cst_nb, 'APPROVED'),
    (2,  'CND-2026-002', 'Marcus Bennett',  'Fictional environmental engineer focused on solar microgrids and green corridors.',   42, 'marcus.bennett@civicflow.demo',  '+1-555-020-0002', @pty_inc,    @cst_nb, 'APPROVED'),
    (3,  'CND-2026-003', 'Nadia Haddad',    'Fictional educator proposing digital literacy centers and library modernization.',     35, 'nadia.haddad@civicflow.demo',    '+1-555-020-0003', @pty_aap,    @cst_nb, 'APPROVED'),
    (4,  'CND-2026-004', 'Owen Sterling',   'Fictional civic economist championing small-business grants in Riverdale Metro.',     45, 'owen.sterling@civicflow.demo',   '+1-555-020-0004', @pty_bjp,    @cst_rm, 'APPROVED'),
    (5,  'CND-2026-005', 'Amara Mensah',    'Fictional sustainability strategist working on clean riverfront restoration.',        33, 'amara.mensah@civicflow.demo',    '+1-555-020-0005', @pty_inc,    @cst_rm, 'APPROVED'),
    (6,  'CND-2026-006', 'Lucas Delgado',   'Fictional community organizer prioritizing Westgate Valley parks and youth sports.',  39, 'lucas.delgado@civicflow.demo',   '+1-555-020-0006', @pty_cjp,    @cst_wv, 'APPROVED'),
    (7,  'CND-2026-007', 'Hannah Lind',     'Fictional civic researcher awaiting candidate review.',                               29, 'hannah.lind@civicflow.demo',     '+1-555-020-0007', @pty_aap,    @cst_wv, 'PENDING'),
    (8,  'CND-2026-008', 'Victor Grant',    'Fictional coastal engineer assigned to East Harbor Ward.',                            48, 'victor.grant@civicflow.demo',    '+1-555-020-0008', @pty_cjp,    @cst_eh, 'APPROVED'),
    (9,  'CND-2026-009', 'Kavita Rao',     'Simulation candidate focused on educational governance and municipal libraries.',     36, 'kavita.rao@civicflow.demo',     '+1-555-020-0009', @pty_jds,    @cst_nb, 'APPROVED'),
    (10, 'CND-2026-010', 'Rajesh Verma',   'Simulation candidate advocating small-enterprise incubators and local job fairs.',     41, 'rajesh.verma@civicflow.demo',   '+1-555-020-0010', @pty_rjp,    @cst_rm, 'APPROVED'),
    (11, 'CND-2026-011', 'Debashis Sen',   'Simulation candidate prioritizing urban transit access and public park rejuvenation.', 38, 'debashis.sen@civicflow.demo',   '+1-555-020-0011', @pty_tmc,    @cst_wv, 'APPROVED'),
    (12, 'CND-2026-012', 'Sunil Bharti',   'Simulation candidate emphasizing clean water access and vocational training programs.', 44, 'sunil.bharti@civicflow.demo',   '+1-555-020-0012', @pty_bsp,    @cst_eh, 'APPROVED'),
    (13, 'CND-2026-013', 'Karthik Selvam', 'Simulation candidate advocating digital public infrastructure and affordable housing.', 39, 'karthik.selvam@civicflow.demo', '+1-555-020-0013', @pty_aiadmk, @cst_nb, 'APPROVED'),
    (14, 'CND-2026-014', 'Anand Pratap',   'Simulation candidate championing youth sports facilities and neighborhood safety.',   34, 'anand.pratap@civicflow.demo',   '+1-555-020-0014', @pty_sp,     @cst_rm, 'APPROVED')
ON DUPLICATE KEY UPDATE
    full_name           = VALUES(full_name),
    profile_description = VALUES(profile_description),
    party_id            = VALUES(party_id),
    constituency_id     = VALUES(constituency_id),
    status              = VALUES(status);

SET @cnd_avery  = (SELECT candidate_id FROM candidates WHERE candidate_code = 'CND-2026-001');
SET @cnd_marcus = (SELECT candidate_id FROM candidates WHERE candidate_code = 'CND-2026-002');
SET @cnd_nadia  = (SELECT candidate_id FROM candidates WHERE candidate_code = 'CND-2026-003');
SET @cnd_owen   = (SELECT candidate_id FROM candidates WHERE candidate_code = 'CND-2026-004');
SET @cnd_amara  = (SELECT candidate_id FROM candidates WHERE candidate_code = 'CND-2026-005');
SET @cnd_lucas  = (SELECT candidate_id FROM candidates WHERE candidate_code = 'CND-2026-006');

-- ----------------------------------------------------------------------------
-- 6. Seed Demonstration Elections (OPEN, PUBLISHED, SCHEDULED, DRAFT)
-- ----------------------------------------------------------------------------
INSERT INTO elections (election_code, election_name, description, election_type, start_datetime, end_datetime, status, created_by) VALUES
    ('ELC-2026-SPR01', '2026 Spring Civic Council Simulation',
     'Active educational simulation open for eligible fictional voters in Northbrook, Riverdale Metro, and Westgate Valley.',
     'COUNCIL', NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 14 DAY, 'OPEN', @uid_super),

    ('ELC-2026-WIN02', '2026 Winter Municipal Mock Poll',
     'Completed and published demonstration election showcasing aggregated simulated results and zero-vote handling.',
     'MOCK_POLL', NOW() - INTERVAL 40 DAY, NOW() - INTERVAL 25 DAY, 'PUBLISHED', @uid_super),

    ('ELC-2026-AUT03', '2026 Autumn Campus Senate Election',
     'Scheduled upcoming educational simulation awaiting opening window.',
     'CAMPUS', NOW() + INTERVAL 10 DAY, NOW() + INTERVAL 20 DAY, 'SCHEDULED', @uid_super),

    ('ELC-2027-DRF04', '2027 Regional Advisory Draft Poll',
     'Draft election ready for administrative configuration and scheduling tests.',
     'GENERAL', NOW() + INTERVAL 45 DAY, NOW() + INTERVAL 60 DAY, 'DRAFT', @uid_super);

SET @elc_open  = (SELECT election_id FROM elections WHERE election_code = 'ELC-2026-SPR01');
SET @elc_pub   = (SELECT election_id FROM elections WHERE election_code = 'ELC-2026-WIN02');
SET @elc_sched = (SELECT election_id FROM elections WHERE election_code = 'ELC-2026-AUT03');

-- Assign constituencies to elections
INSERT INTO election_constituencies (election_id, constituency_id) VALUES
    (@elc_open, @cst_nb), (@elc_open, @cst_rm), (@elc_open, @cst_wv),
    (@elc_pub,  @cst_nb), (@elc_pub,  @cst_rm),
    (@elc_sched,@cst_nb);

-- Assign approved candidates to elections
INSERT INTO election_candidates (election_id, candidate_id, constituency_id) VALUES
    (@elc_open, 1,  @cst_nb),
    (@elc_open, 2,  @cst_nb),
    (@elc_open, 3,  @cst_nb),
    (@elc_open, 4,  @cst_rm),
    (@elc_open, 5,  @cst_rm),
    (@elc_open, 6,  @cst_wv),
    (@elc_open, 9,  @cst_nb),
    (@elc_open, 10, @cst_rm),
    (@elc_open, 11, @cst_wv),
    (@elc_open, 12, @cst_eh),
    (@elc_open, 13, @cst_nb),
    (@elc_open, 14, @cst_rm),

    (@elc_pub,  1,  @cst_nb),
    (@elc_pub,  2,  @cst_nb),
    (@elc_pub,  3,  @cst_nb),
    (@elc_pub,  4,  @cst_rm),
    (@elc_pub,  5,  @cst_rm),

    (@elc_sched,1,  @cst_nb),
    (@elc_sched,2,  @cst_nb);

-- Populate Voter Eligibility
INSERT INTO voter_eligibility (election_id, voter_id, constituency_id, is_eligible) VALUES
    (@elc_open, @vtr_maya,   @cst_nb, TRUE),
    (@elc_open, @vtr_liam,   @cst_nb, TRUE),
    (@elc_open, @vtr_priya,  @cst_rm, TRUE),
    (@elc_open, @vtr_daniel, @cst_rm, TRUE),
    (@elc_open, @vtr_sofia,  @cst_wv, TRUE),

    (@elc_pub,  @vtr_maya,   @cst_nb, TRUE),
    (@elc_pub,  @vtr_liam,   @cst_nb, TRUE),
    (@elc_pub,  @vtr_priya,  @cst_rm, TRUE),
    (@elc_pub,  @vtr_daniel, @cst_rm, TRUE),

    (@elc_sched,@vtr_maya,   @cst_nb, TRUE),
    (@elc_sched,@vtr_liam,   @cst_nb, TRUE);

-- ----------------------------------------------------------------------------
-- 7. Seed Separated Participation & Anonymous Simulated Ballots
-- Note: voter_maya, voter_priya, voter_sofia have NOT voted yet in ELC-2026-SPR01
-- so demonstrators can immediately test the live Simulated Voting Booth!
-- ----------------------------------------------------------------------------
-- Published election: 4 eligible voters all participated
INSERT INTO voter_participation (voter_id, election_id, submitted_at, participation_status) VALUES
    (@vtr_maya,   @elc_pub, NOW() - INTERVAL 35 DAY, 'SUBMITTED'),
    (@vtr_liam,   @elc_pub, NOW() - INTERVAL 34 DAY, 'SUBMITTED'),
    (@vtr_priya,  @elc_pub, NOW() - INTERVAL 33 DAY, 'SUBMITTED'),
    (@vtr_daniel, @elc_pub, NOW() - INTERVAL 32 DAY, 'SUBMITTED');

-- Anonymous ballots for published election (strictly NO voter_id; Avery 2, Owen 1 -> BJP 3 votes [75%]; Marcus 1 -> INC 1 vote [25%])
INSERT INTO anonymous_ballots (election_id, candidate_id, constituency_id, submitted_at) VALUES
    (@elc_pub, @cnd_avery,  @cst_nb, NOW() - INTERVAL 35 DAY),
    (@elc_pub, @cnd_avery,  @cst_nb, NOW() - INTERVAL 34 DAY),
    (@elc_pub, @cnd_owen,   @cst_rm, NOW() - INTERVAL 33 DAY),
    (@elc_pub, @cnd_marcus, @cst_nb, NOW() - INTERVAL 32 DAY);

-- Open election: Liam and Daniel have already participated; Maya, Priya, Sofia can vote live!
INSERT INTO voter_participation (voter_id, election_id, submitted_at, participation_status) VALUES
    (@vtr_liam,   @elc_open, NOW() - INTERVAL 12 HOUR, 'SUBMITTED'),
    (@vtr_daniel, @elc_open, NOW() - INTERVAL 8 HOUR,  'SUBMITTED');

INSERT INTO anonymous_ballots (election_id, candidate_id, constituency_id, submitted_at) VALUES
    (@elc_open, @cnd_avery, @cst_nb, NOW() - INTERVAL 12 HOUR),
    (@elc_open, @cnd_owen,  @cst_rm, NOW() - INTERVAL 8 HOUR);

-- ----------------------------------------------------------------------------
-- 8. Seed Initial Audit Trail Entries (Non-sensitive demo history)
-- ----------------------------------------------------------------------------
INSERT INTO audit_logs (user_id, username, action, module, action_timestamp, details, result_status) VALUES
    (@uid_super, 'superadmin', 'SEED_DEMO_DATA',  'SYSTEM',   NOW() - INTERVAL 40 DAY, 'Initialized fictional CIVICFLOW demonstration dataset', 'SUCCESS'),
    (@uid_super, 'superadmin', 'CREATE_ELECTION', 'ELECTION', NOW() - INTERVAL 40 DAY, 'Created election ELC-2026-WIN02 (2026 Winter Municipal Mock Poll)', 'SUCCESS'),
    (@uid_super, 'superadmin', 'PUBLISH_RESULTS', 'ELECTION', NOW() - INTERVAL 24 DAY, 'Published simulated results for election ELC-2026-WIN02', 'SUCCESS'),
    (@uid_super, 'superadmin', 'OPEN_ELECTION',   'ELECTION', NOW() - INTERVAL 1 DAY,  'Opened simulated election ELC-2026-SPR01 for voting', 'SUCCESS');
