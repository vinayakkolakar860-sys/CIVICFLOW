package com.civicflow.repository.jdbc;

import com.civicflow.config.DatabaseManager;
import com.civicflow.exception.DatabaseException;
import com.civicflow.exception.VotingException;
import com.civicflow.model.VoterParticipation;
import com.civicflow.repository.VotingRepository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * JDBC implementation of transactional simulated voting.
 *
 * CRITICAL DATA SEPARATION:
 * - Inserts voter participation into `voter_participation` (voter_id, election_id, submitted_at) — NO candidate_id.
 * - Inserts anonymous ballot into `anonymous_ballots` (election_id, candidate_id, constituency_id, submitted_at) — NO voter_id.
 * - Both inserts execute atomically inside ONE database transaction with SERIALIZABLE/REPEATABLE_READ isolation
 *   and UNIQUE(voter_id, election_id) constraint enforcement to prevent concurrent duplicate voting.
 */
public class JdbcVotingRepository implements VotingRepository {

    private final DatabaseManager db;

    public JdbcVotingRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public boolean isVoterEligible(long electionId, long voterId, long constituencyId) {
        String sql = """
                SELECT 1
                FROM voter_eligibility ve
                INNER JOIN election_constituencies ec
                    ON ec.election_id = ve.election_id AND ec.constituency_id = ve.constituency_id
                WHERE ve.election_id = ? AND ve.voter_id = ? AND ve.constituency_id = ? AND ve.is_eligible = TRUE
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, electionId);
            ps.setLong(2, voterId);
            ps.setLong(3, constituencyId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to verify voter election eligibility.", e);
        }
    }

    @Override
    public boolean hasParticipated(long electionId, long voterId) {
        String sql = "SELECT 1 FROM voter_participation WHERE election_id = ? AND voter_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, electionId);
            ps.setLong(2, voterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to verify voter participation status.", e);
        }
    }

    @Override
    public List<VoterParticipation> findParticipationByVoter(long voterId) {
        String sql = """
                SELECT vp.participation_id, vp.voter_id, vp.election_id,
                       e.election_name, e.election_code, vp.submitted_at, vp.participation_status
                FROM voter_participation vp
                INNER JOIN elections e ON vp.election_id = e.election_id
                WHERE vp.voter_id = ?
                ORDER BY vp.submitted_at DESC
                """;
        List<VoterParticipation> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, voterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapParticipation(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to retrieve voter participation history.", e);
        }
    }

    @Override
    public List<VoterParticipation> findParticipationByElection(long electionId) {
        String sql = """
                SELECT vp.participation_id, vp.voter_id, vp.election_id,
                       e.election_name, e.election_code, vp.submitted_at, vp.participation_status
                FROM voter_participation vp
                INNER JOIN elections e ON vp.election_id = e.election_id
                WHERE vp.election_id = ?
                ORDER BY vp.submitted_at DESC
                """;
        List<VoterParticipation> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, electionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapParticipation(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to retrieve election participation records.", e);
        }
    }

    @Override
    public Map<String, Long> countParticipationByElection() {
        String sql = """
                SELECT e.election_name, COUNT(vp.participation_id) AS cnt
                FROM elections e
                LEFT JOIN voter_participation vp ON vp.election_id = e.election_id
                GROUP BY e.election_id, e.election_name
                ORDER BY e.start_datetime DESC
                LIMIT 10
                """;
        Map<String, Long> map = new LinkedHashMap<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("election_name"), rs.getLong("cnt"));
            }
            return map;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to aggregate election participation counts.", e);
        }
    }

    @Override
    public long submitVoteAtomically(long electionId, long voterId, long candidateId, long constituencyId, LocalDateTime submittedAt) {
        Timestamp ts = Timestamp.valueOf(submittedAt);
        try {
            return db.executeInTransaction(Connection.TRANSACTION_REPEATABLE_READ, conn -> {
                // Step 1: Insert participation record (UNIQUE constraint on voter_id + election_id blocks duplicates)
                // Strictly NO candidate_id stored here.
                String insertPartSql = """
                        INSERT INTO voter_participation (voter_id, election_id, submitted_at, participation_status)
                        VALUES (?, ?, ?, 'SUBMITTED')
                        """;
                long participationId;
                try (PreparedStatement psPart = conn.prepareStatement(insertPartSql, Statement.RETURN_GENERATED_KEYS)) {
                    psPart.setLong(1, voterId);
                    psPart.setLong(2, electionId);
                    psPart.setTimestamp(3, ts);
                    psPart.executeUpdate();
                    try (ResultSet keys = psPart.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Failed to confirm participation record ID.");
                        }
                        participationId = keys.getLong(1);
                    }
                }

                // Step 2: Insert anonymous ballot record (Strictly NO voter_id stored here)
                String insertBallotSql = """
                        INSERT INTO anonymous_ballots (election_id, candidate_id, constituency_id, submitted_at)
                        VALUES (?, ?, ?, ?)
                        """;
                try (PreparedStatement psBallot = conn.prepareStatement(insertBallotSql)) {
                    psBallot.setLong(1, electionId);
                    psBallot.setLong(2, candidateId);
                    psBallot.setLong(3, constituencyId);
                    psBallot.setTimestamp(4, ts);
                    psBallot.executeUpdate();
                }

                return participationId;
            });
        } catch (DatabaseException ex) {
            if (ex.getCause() instanceof SQLException sqlEx) {
                int code = sqlEx.getErrorCode();
                if (code == 1062 || "23000".equals(sqlEx.getSQLState())) {
                    throw new VotingException("Duplicate vote rejected: your participation in this election has already been recorded.", sqlEx);
                }
                if (code == 1213 || code == 1205) {
                    throw new VotingException("Concurrent vote submission detected. Duplicate participation is not permitted.", sqlEx);
                }
            }
            throw new VotingException("Simulated ballot submission failed and was safely rolled back.", ex);
        }
    }

    private VoterParticipation mapParticipation(ResultSet rs) throws SQLException {
        VoterParticipation vp = new VoterParticipation();
        vp.setParticipationId(rs.getLong("participation_id"));
        vp.setVoterId(rs.getLong("voter_id"));
        vp.setElectionId(rs.getLong("election_id"));
        vp.setElectionName(rs.getString("election_name"));
        vp.setElectionCode(rs.getString("election_code"));
        Timestamp ts = rs.getTimestamp("submitted_at");
        vp.setSubmittedAt(ts != null ? ts.toLocalDateTime() : null);
        vp.setParticipationStatus(rs.getString("participation_status"));
        return vp;
    }
}
