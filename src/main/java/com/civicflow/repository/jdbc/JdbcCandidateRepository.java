package com.civicflow.repository.jdbc;

import com.civicflow.config.DatabaseManager;
import com.civicflow.exception.DatabaseException;
import com.civicflow.exception.DuplicateRecordException;
import com.civicflow.model.Candidate;
import com.civicflow.model.CandidateStatus;
import com.civicflow.repository.CandidateRepository;

import java.sql.*;
import java.util.*;

public class JdbcCandidateRepository implements CandidateRepository {

    private final DatabaseManager db;

    private static final String BASE_SELECT = """
            SELECT ca.candidate_id, ca.candidate_code, ca.full_name, ca.profile_description,
                   ca.age, ca.email, ca.contact_number, ca.party_id, p.party_name, p.party_code,
                   ca.constituency_id, c.constituency_name, c.constituency_code,
                   ca.photo_path, ca.status, ca.created_at, ca.updated_at
            FROM candidates ca
            INNER JOIN parties p ON ca.party_id = p.party_id
            INNER JOIN constituencies c ON ca.constituency_id = c.constituency_id
            """;

    public JdbcCandidateRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public List<Candidate> search(String query, Long constituencyId, Long partyId, CandidateStatus status) {
        StringBuilder sql = new StringBuilder(BASE_SELECT).append(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            sql.append(" AND (LOWER(ca.candidate_code) LIKE ? OR LOWER(ca.full_name) LIKE ? OR LOWER(ca.email) LIKE ? OR LOWER(p.party_name) LIKE ?) ");
            String like = "%" + query.trim().toLowerCase() + "%";
            for (int i = 0; i < 4; i++) params.add(like);
        }
        if (constituencyId != null) {
            sql.append(" AND ca.constituency_id = ? ");
            params.add(constituencyId);
        }
        if (partyId != null) {
            sql.append(" AND ca.party_id = ? ");
            params.add(partyId);
        }
        if (status != null) {
            sql.append(" AND ca.status = ? ");
            params.add(status.name());
        }
        sql.append(" ORDER BY ca.full_name ASC");

        List<Candidate> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to fetch candidates.", e);
        }
    }

    @Override
    public Optional<Candidate> findById(long candidateId) {
        String sql = BASE_SELECT + " WHERE ca.candidate_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, candidateId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to fetch candidate details.", e);
        }
    }

    @Override
    public Optional<Candidate> findByEmail(String email) {
        String sql = BASE_SELECT + " WHERE LOWER(ca.email) = LOWER(?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to look up candidate by email.", e);
        }
    }

    @Override
    public Optional<Candidate> findByCode(String candidateCode) {
        String sql = BASE_SELECT + " WHERE UPPER(ca.candidate_code) = UPPER(?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, candidateCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to look up candidate by code.", e);
        }
    }

    @Override
    public long insert(Candidate candidate) {
        String sql = """
                INSERT INTO candidates (candidate_code, full_name, profile_description, age, email,
                                        contact_number, party_id, constituency_id, photo_path, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, candidate.getCandidateCode());
            ps.setString(2, candidate.getFullName());
            ps.setString(3, candidate.getProfileDescription());
            ps.setInt(4, candidate.getAge());
            ps.setString(5, candidate.getEmail());
            ps.setString(6, candidate.getContactNumber());
            ps.setLong(7, candidate.getPartyId());
            ps.setLong(8, candidate.getConstituencyId());
            ps.setString(9, candidate.getPhotoPath());
            ps.setString(10, candidate.getStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    candidate.setCandidateId(id);
                    return id;
                }
                throw new SQLException("Failed to get generated candidate_id.");
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                throw new DuplicateRecordException("A candidate with that code or email already exists.", e);
            }
            throw new DatabaseException("Failed to register candidate.", e);
        }
    }

    @Override
    public void update(Candidate candidate) {
        String sql = """
                UPDATE candidates
                SET full_name = ?, profile_description = ?, age = ?, email = ?, contact_number = ?,
                    party_id = ?, constituency_id = ?, photo_path = ?
                WHERE candidate_id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, candidate.getFullName());
            ps.setString(2, candidate.getProfileDescription());
            ps.setInt(3, candidate.getAge());
            ps.setString(4, candidate.getEmail());
            ps.setString(5, candidate.getContactNumber());
            ps.setLong(6, candidate.getPartyId());
            ps.setLong(7, candidate.getConstituencyId());
            ps.setString(8, candidate.getPhotoPath());
            ps.setLong(9, candidate.getCandidateId());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                throw new DuplicateRecordException("A candidate with that email already exists.", e);
            }
            throw new DatabaseException("Failed to update candidate.", e);
        }
    }

    @Override
    public void updateStatus(long candidateId, CandidateStatus status) {
        String sql = "UPDATE candidates SET status = ? WHERE candidate_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, candidateId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update candidate status.", e);
        }
    }

    @Override
    public boolean isAssignedToProtectedElection(long candidateId) {
        String sql = """
                SELECT (
                    (SELECT COUNT(*) FROM election_candidates ec
                     INNER JOIN elections e ON ec.election_id = e.election_id
                     WHERE ec.candidate_id = ? AND e.status IN ('OPEN', 'CLOSED', 'PUBLISHED'))
                    +
                    (SELECT COUNT(*) FROM anonymous_ballots WHERE candidate_id = ?)
                ) AS cnt
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, candidateId);
            ps.setLong(2, candidateId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong("cnt") > 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to check candidate election links.", e);
        }
    }

    @Override
    public void delete(long candidateId) {
        String sql = "DELETE FROM candidates WHERE candidate_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, candidateId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Cannot delete candidate because election records reference them.", e);
        }
    }

    @Override
    public long countAll() {
        String sql = "SELECT COUNT(*) FROM candidates";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to count candidates.", e);
        }
    }

    @Override
    public Map<String, Long> countByParty() {
        String sql = """
                SELECT p.party_name, COUNT(ca.candidate_id) AS cnt
                FROM parties p
                LEFT JOIN candidates ca ON ca.party_id = p.party_id
                GROUP BY p.party_id, p.party_name
                ORDER BY cnt DESC, p.party_name ASC
                """;
        Map<String, Long> map = new LinkedHashMap<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("party_name"), rs.getLong("cnt"));
            }
            return map;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to aggregate candidates by party.", e);
        }
    }

    private Candidate mapRow(ResultSet rs) throws SQLException {
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
        Timestamp cr = rs.getTimestamp("created_at");
        ca.setCreatedAt(cr != null ? cr.toLocalDateTime() : null);
        Timestamp up = rs.getTimestamp("updated_at");
        ca.setUpdatedAt(up != null ? up.toLocalDateTime() : null);
        return ca;
    }
}
