package com.civicflow.repository.jdbc;

import com.civicflow.config.DatabaseManager;
import com.civicflow.exception.DatabaseException;
import com.civicflow.exception.DuplicateRecordException;
import com.civicflow.model.*;
import com.civicflow.repository.ElectionRepository;

import java.sql.*;
import java.util.*;

public class JdbcElectionRepository implements ElectionRepository {

    private final DatabaseManager db;

    private static final String BASE_SELECT = """
            SELECT e.election_id, e.election_code, e.election_name, e.description, e.election_type,
                   e.start_datetime, e.end_datetime, e.status, e.created_by, u.full_name AS created_by_name,
                   e.created_at, e.updated_at,
                   (SELECT COUNT(*) FROM election_constituencies ec WHERE ec.election_id = e.election_id) AS const_count,
                   (SELECT COUNT(*) FROM election_candidates ecand WHERE ecand.election_id = e.election_id) AS cand_count,
                   (SELECT COUNT(*) FROM voter_participation vp WHERE vp.election_id = e.election_id) AS part_count
            FROM elections e
            INNER JOIN users u ON e.created_by = u.user_id
            """;

    public JdbcElectionRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public List<Election> findAll(String search, ElectionStatus statusFilter) {
        StringBuilder sql = new StringBuilder(BASE_SELECT).append(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(e.election_code) LIKE ? OR LOWER(e.election_name) LIKE ? OR LOWER(COALESCE(e.description,'')) LIKE ?) ");
            String like = "%" + search.trim().toLowerCase() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (statusFilter != null) {
            sql.append(" AND e.status = ? ");
            params.add(statusFilter.name());
        }
        sql.append(" ORDER BY e.start_datetime DESC, e.election_id DESC");

        List<Election> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapElection(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to fetch elections.", e);
        }
    }

    @Override
    public List<Election> findByConstituency(long constituencyId) {
        String sql = BASE_SELECT + """
                INNER JOIN election_constituencies ec2 ON e.election_id = ec2.election_id
                WHERE ec2.constituency_id = ?
                ORDER BY e.start_datetime DESC
                """;
        List<Election> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, constituencyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapElection(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to fetch election history for constituency.", e);
        }
    }

    @Override
    public List<Election> findEligibleForVoter(long voterId) {
        String sql = BASE_SELECT + """
                INNER JOIN voter_eligibility ve ON e.election_id = ve.election_id
                WHERE ve.voter_id = ? AND ve.is_eligible = TRUE AND e.status IN ('SCHEDULED', 'OPEN', 'CLOSED', 'PUBLISHED')
                ORDER BY FIELD(e.status, 'OPEN', 'SCHEDULED', 'PUBLISHED', 'CLOSED'), e.start_datetime DESC
                """;
        List<Election> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, voterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapElection(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to fetch eligible elections for voter.", e);
        }
    }

    @Override
    public List<Election> findPublishedElections() {
        return findAll(null, ElectionStatus.PUBLISHED);
    }

    @Override
    public Optional<Election> findById(long electionId) {
        String sql = BASE_SELECT + " WHERE e.election_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, electionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapElection(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to fetch election details.", e);
        }
    }

    @Override
    public Optional<Election> findByCode(String electionCode) {
        String sql = BASE_SELECT + " WHERE UPPER(e.election_code) = UPPER(?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, electionCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapElection(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to look up election by code.", e);
        }
    }

    @Override
    public long insert(Election election) {
        String sql = """
                INSERT INTO elections (election_code, election_name, description, election_type,
                                       start_datetime, end_datetime, status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, election.getElectionCode());
            ps.setString(2, election.getElectionName());
            ps.setString(3, election.getDescription());
            ps.setString(4, election.getElectionType().name());
            ps.setTimestamp(5, Timestamp.valueOf(election.getStartDateTime()));
            ps.setTimestamp(6, Timestamp.valueOf(election.getEndDateTime()));
            ps.setString(7, election.getStatus().name());
            ps.setLong(8, election.getCreatedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    election.setElectionId(id);
                    return id;
                }
                throw new SQLException("Failed to obtain generated election_id.");
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                throw new DuplicateRecordException("An election with code '" + election.getElectionCode() + "' already exists.", e);
            }
            throw new DatabaseException("Failed to create election.", e);
        }
    }

    @Override
    public void update(Election election) {
        String sql = """
                UPDATE elections
                SET election_name = ?, description = ?, election_type = ?, start_datetime = ?, end_datetime = ?
                WHERE election_id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, election.getElectionName());
            ps.setString(2, election.getDescription());
            ps.setString(3, election.getElectionType().name());
            ps.setTimestamp(4, Timestamp.valueOf(election.getStartDateTime()));
            ps.setTimestamp(5, Timestamp.valueOf(election.getEndDateTime()));
            ps.setLong(6, election.getElectionId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update election.", e);
        }
    }

    @Override
    public void updateStatus(long electionId, ElectionStatus newStatus) {
        String sql = "UPDATE elections SET status = ? WHERE election_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus.name());
            ps.setLong(2, electionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update election status.", e);
        }
    }

    @Override
    public void assignConstituency(long electionId, long constituencyId) {
        String sql = "INSERT IGNORE INTO election_constituencies (election_id, constituency_id) VALUES (?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, electionId);
            ps.setLong(2, constituencyId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to assign constituency to election.", e);
        }
    }

    @Override
    public void removeConstituency(long electionId, long constituencyId) {
        db.executeInTransaction(conn -> {
            try (PreparedStatement ps1 = conn.prepareStatement(
                    "DELETE FROM election_candidates WHERE election_id = ? AND constituency_id = ?")) {
                ps1.setLong(1, electionId);
                ps1.setLong(2, constituencyId);
                ps1.executeUpdate();
            }
            try (PreparedStatement ps2 = conn.prepareStatement(
                    "DELETE FROM voter_eligibility WHERE election_id = ? AND constituency_id = ?")) {
                ps2.setLong(1, electionId);
                ps2.setLong(2, constituencyId);
                ps2.executeUpdate();
            }
            try (PreparedStatement ps3 = conn.prepareStatement(
                    "DELETE FROM election_constituencies WHERE election_id = ? AND constituency_id = ?")) {
                ps3.setLong(1, electionId);
                ps3.setLong(2, constituencyId);
                ps3.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<Constituency> findAssignedConstituencies(long electionId) {
        String sql = """
                SELECT c.constituency_id, c.constituency_code, c.constituency_name, c.city, c.state,
                       c.description, c.status, c.created_at, c.updated_at,
                       (SELECT COUNT(*) FROM voters v WHERE v.constituency_id = c.constituency_id AND v.verification_status = 'APPROVED' AND v.account_status = 'ACTIVE') AS voter_count,
                       (SELECT COUNT(*) FROM election_candidates ec WHERE ec.election_id = ? AND ec.constituency_id = c.constituency_id) AS cand_count
                FROM election_constituencies elc
                INNER JOIN constituencies c ON elc.constituency_id = c.constituency_id
                WHERE elc.election_id = ?
                ORDER BY c.constituency_code ASC
                """;
        List<Constituency> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, electionId);
            ps.setLong(2, electionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Constituency c = new Constituency();
                    c.setConstituencyId(rs.getLong("constituency_id"));
                    c.setConstituencyCode(rs.getString("constituency_code"));
                    c.setConstituencyName(rs.getString("constituency_name"));
                    c.setCity(rs.getString("city"));
                    c.setState(rs.getString("state"));
                    c.setDescription(rs.getString("description"));
                    c.setStatus(ConstituencyStatus.valueOf(rs.getString("status")));
                    c.setVoterCount(rs.getLong("voter_count"));
                    c.setCandidateCount(rs.getLong("cand_count"));
                    list.add(c);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to fetch assigned constituencies.", e);
        }
    }

    @Override
    public void assignCandidate(long electionId, long candidateId, long constituencyId) {
        String sql = "INSERT INTO election_candidates (election_id, candidate_id, constituency_id) VALUES (?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, electionId);
            ps.setLong(2, candidateId);
            ps.setLong(3, constituencyId);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                throw new DuplicateRecordException("This candidate is already assigned to this election.", e);
            }
            throw new DatabaseException("Failed to assign candidate to election.", e);
        }
    }

    @Override
    public void removeCandidate(long electionId, long candidateId) {
        String sql = "DELETE FROM election_candidates WHERE election_id = ? AND candidate_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, electionId);
            ps.setLong(2, candidateId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to remove candidate from election.", e);
        }
    }

    @Override
    public List<Candidate> findAssignedCandidates(long electionId, Long constituencyId) {
        StringBuilder sql = new StringBuilder("""
                SELECT ca.candidate_id, ca.candidate_code, ca.full_name, ca.profile_description,
                       ca.age, ca.email, ca.contact_number, ca.party_id, p.party_name, p.party_code,
                       ec.constituency_id, c.constituency_name, c.constituency_code,
                       ca.photo_path, ca.status, ca.created_at, ca.updated_at
                FROM election_candidates ec
                INNER JOIN candidates ca ON ec.candidate_id = ca.candidate_id
                INNER JOIN parties p ON ca.party_id = p.party_id
                INNER JOIN constituencies c ON ec.constituency_id = c.constituency_id
                WHERE ec.election_id = ?
                """);
        if (constituencyId != null) {
            sql.append(" AND ec.constituency_id = ? ");
        }
        sql.append(" ORDER BY c.constituency_name ASC, ca.full_name ASC");

        List<Candidate> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setLong(1, electionId);
            if (constituencyId != null) {
                ps.setLong(2, constituencyId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Candidate ca = new Candidate();
                    ca.setCandidateId(rs.getLong("candidate_id"));
                    ca.setCandidateCode(rs.getString("candidate_code"));
                    ca.setFullName(rs.getString("full_name"));
                    ca.setProfileDescription(rs.getString("profile_description"));
                    ca.setAge(rs.getInt("age"));
                    ca.setEmail(rs.getString("email"));
                    ca.setContactNumber(rs.getString("contact_number"));
                    ca.setPartyId(rs.getLong("party_id"));
                    ca.setPartyName(rs.getString("party_name"));
                    ca.setPartyCode(rs.getString("party_code"));
                    ca.setConstituencyId(rs.getLong("constituency_id"));
                    ca.setConstituencyName(rs.getString("constituency_name"));
                    ca.setConstituencyCode(rs.getString("constituency_code"));
                    ca.setPhotoPath(rs.getString("photo_path"));
                    ca.setStatus(CandidateStatus.valueOf(rs.getString("status")));
                    list.add(ca);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to fetch election candidates.", e);
        }
    }

    @Override
    public boolean isCandidateAssigned(long electionId, long candidateId, long constituencyId) {
        String sql = "SELECT 1 FROM election_candidates WHERE election_id = ? AND candidate_id = ? AND constituency_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, electionId);
            ps.setLong(2, candidateId);
            ps.setLong(3, constituencyId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to verify candidate election eligibility.", e);
        }
    }

    @Override
    public int syncVoterEligibilityForElection(long electionId) {
        String sql = """
                INSERT INTO voter_eligibility (election_id, voter_id, constituency_id, is_eligible)
                SELECT ec.election_id, v.voter_id, v.constituency_id, TRUE
                FROM election_constituencies ec
                INNER JOIN voters v ON v.constituency_id = ec.constituency_id
                WHERE ec.election_id = ?
                  AND v.verification_status = 'APPROVED'
                  AND v.account_status = 'ACTIVE'
                ON DUPLICATE KEY UPDATE is_eligible = TRUE, constituency_id = VALUES(constituency_id)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, electionId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to synchronize voter eligibility for election.", e);
        }
    }

    @Override
    public long countAll() {
        String sql = "SELECT COUNT(*) FROM elections";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to count elections.", e);
        }
    }

    @Override
    public Map<String, Long> countByStatus() {
        String sql = "SELECT status, COUNT(*) AS cnt FROM elections GROUP BY status";
        Map<String, Long> map = new LinkedHashMap<>();
        for (ElectionStatus s : ElectionStatus.values()) {
            map.put(s.name(), 0L);
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("status"), rs.getLong("cnt"));
            }
            return map;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to aggregate elections by status.", e);
        }
    }

    private Election mapElection(ResultSet rs) throws SQLException {
        Election e = new Election();
        e.setElectionId(rs.getLong("election_id"));
        e.setElectionCode(rs.getString("election_code"));
        e.setElectionName(rs.getString("election_name"));
        e.setDescription(rs.getString("description"));
        e.setElectionType(ElectionType.valueOf(rs.getString("election_type")));
        e.setStartDateTime(rs.getTimestamp("start_datetime").toLocalDateTime());
        e.setEndDateTime(rs.getTimestamp("end_datetime").toLocalDateTime());
        e.setStatus(ElectionStatus.valueOf(rs.getString("status")));
        e.setCreatedBy(rs.getLong("created_by"));
        e.setCreatedByName(rs.getString("created_by_name"));
        Timestamp cr = rs.getTimestamp("created_at");
        e.setCreatedAt(cr != null ? cr.toLocalDateTime() : null);
        Timestamp up = rs.getTimestamp("updated_at");
        e.setUpdatedAt(up != null ? up.toLocalDateTime() : null);
        e.setConstituencyCount(rs.getInt("const_count"));
        e.setCandidateCount(rs.getInt("cand_count"));
        e.setTotalParticipants(rs.getLong("part_count"));
        return e;
    }
}
