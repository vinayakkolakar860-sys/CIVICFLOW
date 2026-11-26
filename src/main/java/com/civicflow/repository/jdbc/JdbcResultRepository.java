package com.civicflow.repository.jdbc;

import com.civicflow.config.DatabaseManager;
import com.civicflow.exception.DatabaseException;
import com.civicflow.model.CandidateResult;
import com.civicflow.model.ConstituencyResult;
import com.civicflow.repository.ResultRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates election results using SQL aggregation queries over `anonymous_ballots`.
 * Uses LEFT JOIN from `election_candidates` so zero-vote candidates are accurately reported with 0 votes.
 */
public class JdbcResultRepository implements ResultRepository {

    private final DatabaseManager db;

    public JdbcResultRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public List<CandidateResult> aggregateCandidateResults(long electionId) {
        String sql = """
                SELECT ec.candidate_id, ca.candidate_code, ca.full_name,
                       p.party_code, p.party_name,
                       ec.constituency_id, c.constituency_code, c.constituency_name,
                       COUNT(ab.ballot_id) AS vote_count
                FROM election_candidates ec
                INNER JOIN candidates ca ON ec.candidate_id = ca.candidate_id
                INNER JOIN parties p ON ca.party_id = p.party_id
                INNER JOIN constituencies c ON ec.constituency_id = c.constituency_id
                LEFT JOIN anonymous_ballots ab
                    ON ab.election_id = ec.election_id
                   AND ab.candidate_id = ec.candidate_id
                   AND ab.constituency_id = ec.constituency_id
                WHERE ec.election_id = ?
                GROUP BY ec.candidate_id, ca.candidate_code, ca.full_name,
                         p.party_code, p.party_name,
                         ec.constituency_id, c.constituency_code, c.constituency_name
                ORDER BY c.constituency_name ASC, vote_count DESC, ca.full_name ASC
                """;
        List<CandidateResult> results = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, electionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CandidateResult r = new CandidateResult();
                    r.setCandidateId(rs.getLong("candidate_id"));
                    r.setCandidateCode(rs.getString("candidate_code"));
                    r.setCandidateName(rs.getString("full_name"));
                    r.setPartyCode(rs.getString("party_code"));
                    r.setPartyName(rs.getString("party_name"));
                    r.setConstituencyId(rs.getLong("constituency_id"));
                    r.setConstituencyCode(rs.getString("constituency_code"));
                    r.setConstituencyName(rs.getString("constituency_name"));
                    r.setVoteCount(rs.getLong("vote_count"));
                    results.add(r);
                }
            }
            return results;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to aggregate candidate vote totals.", e);
        }
    }

    @Override
    public List<ConstituencyResult> aggregateConstituencyStats(long electionId) {
        String sql = """
                SELECT c.constituency_id, c.constituency_code, c.constituency_name,
                       (SELECT COUNT(*) FROM voter_eligibility ve
                        WHERE ve.election_id = ec.election_id AND ve.constituency_id = c.constituency_id AND ve.is_eligible = TRUE) AS eligible_count,
                       (SELECT COUNT(*) FROM voter_participation vp
                        INNER JOIN voters v ON vp.voter_id = v.voter_id
                        WHERE vp.election_id = ec.election_id AND v.constituency_id = c.constituency_id) AS part_count,
                       (SELECT COUNT(*) FROM anonymous_ballots ab
                        WHERE ab.election_id = ec.election_id AND ab.constituency_id = c.constituency_id) AS ballot_count
                FROM election_constituencies ec
                INNER JOIN constituencies c ON ec.constituency_id = c.constituency_id
                WHERE ec.election_id = ?
                ORDER BY c.constituency_name ASC
                """;
        List<ConstituencyResult> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, electionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ConstituencyResult cr = new ConstituencyResult();
                    cr.setConstituencyId(rs.getLong("constituency_id"));
                    cr.setConstituencyCode(rs.getString("constituency_code"));
                    cr.setConstituencyName(rs.getString("constituency_name"));
                    long eligible = rs.getLong("eligible_count");
                    long participants = rs.getLong("part_count");
                    long ballots = rs.getLong("ballot_count");
                    cr.setEligibleVoters(eligible);
                    cr.setTotalParticipants(participants);
                    cr.setTotalBallots(ballots);
                    cr.setTurnoutPercentage(eligible > 0 ? (participants * 100.0) / eligible : 0.0);
                    list.add(cr);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to aggregate constituency result statistics.", e);
        }
    }

    @Override
    public long countEligibleVoters(long electionId) {
        return singleCount("SELECT COUNT(*) FROM voter_eligibility WHERE election_id = ? AND is_eligible = TRUE", electionId);
    }

    @Override
    public long countTotalParticipants(long electionId) {
        return singleCount("SELECT COUNT(*) FROM voter_participation WHERE election_id = ?", electionId);
    }

    @Override
    public long countTotalBallots(long electionId) {
        return singleCount("SELECT COUNT(*) FROM anonymous_ballots WHERE election_id = ?", electionId);
    }

    private long singleCount(String sql, long electionId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, electionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to compute election summary count.", e);
        }
    }
}
