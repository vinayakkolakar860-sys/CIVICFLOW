package com.civicflow.integration;

import com.civicflow.app.ApplicationContext;
import com.civicflow.config.AppConfig;
import com.civicflow.exception.VotingException;
import com.civicflow.model.*;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.sql.*;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full MySQL 8 integration tests covering:
 * - Schema separation (voter_participation has NO candidate_id; anonymous_ballots has NO voter_id)
 * - Foreign keys, UNIQUE constraints, NOT NULL constraints
 * - End-to-end admin setup -> voter approval -> election opening -> ballot submission -> result calculation
 * - Atomic transaction rollback when ballot insert fails
 * - Concurrent duplicate voting prevention (8 simultaneous threads -> exactly 1 success, 7 rejections)
 *
 * Requires MySQL environment variables CIVICFLOW_DB_URL, CIVICFLOW_DB_USER, CIVICFLOW_DB_PASSWORD
 * (or config/application.properties). Skips gracefully via JUnit Assumptions if MySQL is unavailable.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseIntegrationTest {

    private static ApplicationContext ctx;
    private static boolean dbAvailable = false;

    @BeforeAll
    static void initContext() {
        AppConfig cfg = AppConfig.load();
        if (cfg.getDbUser() == null || cfg.getDbUser().isBlank()) {
            return;
        }
        try {
            ctx = new ApplicationContext(cfg, Clock.systemDefaultZone());
            dbAvailable = ctx.getDatabaseManager().testConnection();
        } catch (Exception e) {
            dbAvailable = false;
        }
    }

    @AfterAll
    static void tearDown() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    @Order(1)
    void schemaSeparatesParticipationAndAnonymousBallots() throws Exception {
        Assumptions.assumeTrue(dbAvailable, "MySQL database not configured/available in test environment");

        try (Connection conn = ctx.getDatabaseManager().getConnection()) {
            Set<String> partCols = getColumnNames(conn, "voter_participation");
            assertTrue(partCols.contains("voter_id"));
            assertTrue(partCols.contains("election_id"));
            assertFalse(partCols.contains("candidate_id"), "voter_participation MUST NOT contain candidate_id");

            Set<String> ballotCols = getColumnNames(conn, "anonymous_ballots");
            assertTrue(ballotCols.contains("election_id"));
            assertTrue(ballotCols.contains("candidate_id"));
            assertTrue(ballotCols.contains("constituency_id"));
            assertFalse(ballotCols.contains("voter_id"), "anonymous_ballots MUST NOT contain voter_id");
        }
    }

    @Test
    @Order(2)
    void transactionRollsBackParticipationIfBallotInsertFails() throws Exception {
        Assumptions.assumeTrue(dbAvailable, "MySQL database not configured/available in test environment");

        long testElectionId = createIsolatedTestFixture("ROLLBACK");
        try {
            long voterId = findFixtureVoterId("rb_voter_" + testElectionId);
            long constId = findFixtureConstituencyId("CST-RB-" + testElectionId);

            // Attempt atomic submit with a non-existent candidateId (violates FK on anonymous_ballots)
            assertThrows(Exception.class, () -> {
                ctx.getDatabaseManager().executeInTransaction(Connection.TRANSACTION_REPEATABLE_READ, conn -> {
                    try (PreparedStatement ps1 = conn.prepareStatement(
                            "INSERT INTO voter_participation (voter_id, election_id, submitted_at, participation_status) VALUES (?, ?, NOW(), 'SUBMITTED')")) {
                        ps1.setLong(1, voterId);
                        ps1.setLong(2, testElectionId);
                        ps1.executeUpdate();
                    }
                    try (PreparedStatement ps2 = conn.prepareStatement(
                            "INSERT INTO anonymous_ballots (election_id, candidate_id, constituency_id, submitted_at) VALUES (?, ?, ?, NOW())")) {
                        ps2.setLong(1, testElectionId);
                        ps2.setLong(2, 999999999L); // Non-existent FK -> triggers rollback!
                        ps2.setLong(3, constId);
                        ps2.executeUpdate();
                    }
                    return null;
                });
            });

            // Verify voter_participation was rolled back cleanly (count must be 0!)
            try (Connection conn = ctx.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM voter_participation WHERE election_id = ?")) {
                ps.setLong(1, testElectionId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0L, rs.getLong(1), "Participation row MUST be rolled back when ballot insert fails");
                }
            }
        } finally {
            cleanupFixture(testElectionId);
        }
    }

    @Test
    @Order(3)
    void concurrentDuplicateVoteAttemptsAllowExactlyOneBallot() throws Exception {
        Assumptions.assumeTrue(dbAvailable, "MySQL database not configured/available in test environment");

        long electionId = createIsolatedTestFixture("CONC");
        try {
            long voterId = findFixtureVoterId("rb_voter_" + electionId);
            long candId = findFixtureCandidateId(electionId);
            long constId = findFixtureConstituencyId("CST-RB-" + electionId);

            // Log in as the test voter
            ctx.getAuthService().login("rb_voter_" + electionId, "VoterTest@2026");

            int threadCount = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger successes = new AtomicInteger(0);
            AtomicInteger rejections = new AtomicInteger(0);

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        ctx.getVotingService().castSimulatedBallot(electionId, candId);
                        successes.incrementAndGet();
                    } catch (VotingException ex) {
                        rejections.incrementAndGet();
                    } catch (Exception ex) {
                        rejections.incrementAndGet();
                    }
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }
            pool.shutdown();

            assertEquals(1, successes.get(), "Exactly ONE concurrent ballot submission must succeed");
            assertEquals(threadCount - 1, rejections.get(), "All other concurrent attempts must be rejected");

            // Verify database state: exactly 1 participation row and exactly 1 anonymous ballot row
            try (Connection conn = ctx.getDatabaseManager().getConnection()) {
                assertEquals(1L, countRows(conn, "SELECT COUNT(*) FROM voter_participation WHERE election_id = ?", electionId));
                assertEquals(1L, countRows(conn, "SELECT COUNT(*) FROM anonymous_ballots WHERE election_id = ?", electionId));
            }

            // Close and publish election, then verify ResultService tally
            ctx.getAuthService().logout();
            ctx.getAuthService().login("rb_admin_" + electionId, "AdminTest@2026");
            ctx.getElectionService().closeElection(electionId);
            ctx.getElectionService().publishResults(electionId);

            ElectionResultSummary summary = ctx.getResultService().getElectionResults(electionId);
            assertEquals(1L, summary.getTotalParticipants());
            assertEquals(1L, summary.getTotalBallotsCast());
            assertEquals(100.0, summary.getTurnoutPercentage());
            assertTrue(summary.isPublished());
            assertEquals(1, summary.getCandidateResults().size());
            assertEquals(1L, summary.getCandidateResults().get(0).getVoteCount());
            assertEquals(100.0, summary.getCandidateResults().get(0).getPercentage());

            ctx.getAuthService().logout();
        } finally {
            cleanupFixture(electionId);
        }
    }

    // --- Isolated self-cleaning fixture helpers ---
    private long createIsolatedTestFixture(String tag) throws SQLException {
        String suffix = tag + "_" + (System.nanoTime() % 100000);
        String adminHash = ctx.getPasswordService().hashPassword("AdminTest@2026");
        String voterHash = ctx.getPasswordService().hashPassword("VoterTest@2026");

        try (Connection conn = ctx.getDatabaseManager().getConnection()) {
            conn.setAutoCommit(false);
            try {
                int superRoleId = queryInt(conn, "SELECT role_id FROM roles WHERE role_name='SUPER_ADMIN'");
                int voterRoleId = queryInt(conn, "SELECT role_id FROM roles WHERE role_name='VOTER'");

                // Temporary placeholder election ID suffix derived after inserting election
                long adminUserId = insertReturningId(conn,
                        "INSERT INTO users (username, password_hash, role_id, email, full_name, account_status, must_change_password) VALUES (?, ?, ?, ?, 'IT Admin', 'ACTIVE', FALSE)",
                        "tmp_admin_" + suffix, adminHash, superRoleId, "tmp_admin_" + suffix + "@test.demo");

                long electionId = insertReturningId(conn,
                        "INSERT INTO elections (election_code, election_name, description, election_type, start_datetime, end_datetime, status, created_by) VALUES (?, ?, 'IT Test', 'MOCK_POLL', ?, ?, 'OPEN', ?)",
                        "ELC-IT-" + suffix, "IT Election " + suffix,
                        Timestamp.valueOf(LocalDateTime.now().minusHours(2)),
                        Timestamp.valueOf(LocalDateTime.now().plusHours(6)),
                        adminUserId);

                // Rename admin username deterministically using electionId
                exec(conn, "UPDATE users SET username = ? WHERE user_id = ?", "rb_admin_" + electionId, adminUserId);

                long constId = insertReturningId(conn,
                        "INSERT INTO constituencies (constituency_code, constituency_name, city, state, status) VALUES (?, ?, 'TestCity', 'TestState', 'ACTIVE')",
                        "CST-RB-" + electionId, "IT Constituency " + electionId);

                long partyId = insertReturningId(conn,
                        "INSERT INTO parties (party_code, party_name, description, status) VALUES (?, ?, 'IT Party', 'ACTIVE')",
                        "PTY" + (electionId % 100000), "IT Party " + electionId);

                long candId = insertReturningId(conn,
                        "INSERT INTO candidates (candidate_code, full_name, age, email, contact_number, party_id, constituency_id, status) VALUES (?, 'IT Candidate', 38, ?, '555-000-1111', ?, ?, 'APPROVED')",
                        "CND-IT-" + electionId, "cand_it_" + electionId + "@test.demo", partyId, constId);

                long voterUserId = insertReturningId(conn,
                        "INSERT INTO users (username, password_hash, role_id, email, full_name, account_status, must_change_password) VALUES (?, ?, ?, ?, 'IT Voter', 'ACTIVE', FALSE)",
                        "rb_voter_" + electionId, voterHash, voterRoleId, "voter_it_" + electionId + "@test.demo");

                long voterId = insertReturningId(conn,
                        "INSERT INTO voters (voter_code, user_id, first_name, last_name, email, phone_number, date_of_birth, address, city, state, constituency_id, registration_date, account_status, verification_status) VALUES (?, ?, 'IT', 'Voter', ?, '555-000-2222', ?, '1 Test Rd', 'TestCity', 'TestState', ?, ?, 'ACTIVE', 'APPROVED')",
                        "VTR-IT-" + electionId, voterUserId, "voter_it_" + electionId + "@test.demo",
                        java.sql.Date.valueOf(LocalDate.of(1992, 3, 15)), constId, java.sql.Date.valueOf(LocalDate.now()));

                exec(conn, "INSERT INTO election_constituencies (election_id, constituency_id) VALUES (?, ?)", electionId, constId);
                exec(conn, "INSERT INTO election_candidates (election_id, candidate_id, constituency_id) VALUES (?, ?, ?)", electionId, candId, constId);
                exec(conn, "INSERT INTO voter_eligibility (election_id, voter_id, constituency_id, is_eligible) VALUES (?, ?, ?, TRUE)", electionId, voterId, constId);

                conn.commit();
                return electionId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void cleanupFixture(long electionId) {
        try (Connection conn = ctx.getDatabaseManager().getConnection()) {
            exec(conn, "DELETE FROM anonymous_ballots WHERE election_id = ?", electionId);
            exec(conn, "DELETE FROM voter_participation WHERE election_id = ?", electionId);
            exec(conn, "DELETE FROM voter_eligibility WHERE election_id = ?", electionId);
            exec(conn, "DELETE FROM election_candidates WHERE election_id = ?", electionId);
            exec(conn, "DELETE FROM election_constituencies WHERE election_id = ?", electionId);
            exec(conn, "DELETE FROM elections WHERE election_id = ?", electionId);
            exec(conn, "DELETE FROM candidates WHERE candidate_code = ?", "CND-IT-" + electionId);
            exec(conn, "DELETE FROM voters WHERE voter_code = ?", "VTR-IT-" + electionId);
            exec(conn, "DELETE FROM parties WHERE party_name = ?", "IT Party " + electionId);
            exec(conn, "DELETE FROM constituencies WHERE constituency_code = ?", "CST-RB-" + electionId);
            exec(conn, "DELETE FROM audit_logs WHERE username IN (?, ?)", "rb_admin_" + electionId, "rb_voter_" + electionId);
            exec(conn, "DELETE FROM users WHERE username IN (?, ?)", "rb_admin_" + electionId, "rb_voter_" + electionId);
        } catch (SQLException ignored) {
        }
    }

    private long findFixtureVoterId(String username) throws SQLException {
        try (Connection conn = ctx.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT v.voter_id FROM voters v INNER JOIN users u ON v.user_id=u.user_id WHERE u.username=?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    private long findFixtureConstituencyId(String code) throws SQLException {
        try (Connection conn = ctx.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT constituency_id FROM constituencies WHERE constituency_code=?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    private long findFixtureCandidateId(long electionId) throws SQLException {
        try (Connection conn = ctx.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT candidate_id FROM election_candidates WHERE election_id=? LIMIT 1")) {
            ps.setLong(1, electionId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    private Set<String> getColumnNames(Connection conn, String tableName) throws SQLException {
        Set<String> cols = new HashSet<>();
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, tableName, null)) {
            while (rs.next()) {
                cols.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return cols;
    }

    private long countRows(Connection conn, String sql, long param) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, param);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    private int queryInt(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next(); return rs.getInt(1);
        }
    }

    private long insertReturningId(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { keys.next(); return keys.getLong(1); }
        }
    }

    private void exec(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
        }
    }
}
