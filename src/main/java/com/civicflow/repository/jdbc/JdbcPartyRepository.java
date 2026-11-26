package com.civicflow.repository.jdbc;

import com.civicflow.config.DatabaseManager;
import com.civicflow.exception.DatabaseException;
import com.civicflow.exception.DuplicateRecordException;
import com.civicflow.model.Party;
import com.civicflow.model.PartyStatus;
import com.civicflow.repository.PartyRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcPartyRepository implements PartyRepository {

    private final DatabaseManager db;

    private static final String BASE_SELECT = """
            SELECT p.party_id, p.party_code, p.party_name, p.description, p.logo_path,
                   p.status, p.created_at, p.updated_at,
                   (SELECT COUNT(*) FROM candidates c WHERE c.party_id = p.party_id) AS candidate_count
            FROM parties p
            """;

    public JdbcPartyRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public List<Party> findAll(String search, PartyStatus statusFilter) {
        StringBuilder sql = new StringBuilder(BASE_SELECT).append(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(p.party_code) LIKE ? OR LOWER(p.party_name) LIKE ? OR LOWER(COALESCE(p.description,'')) LIKE ?) ");
            String like = "%" + search.trim().toLowerCase() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (statusFilter != null) {
            sql.append(" AND p.status = ? ");
            params.add(statusFilter.name());
        }
        sql.append(" ORDER BY p.party_name ASC");

        List<Party> list = new ArrayList<>();
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
            throw new DatabaseException("Failed to fetch fictional parties.", e);
        }
    }

    @Override
    public Optional<Party> findById(long partyId) {
        String sql = BASE_SELECT + " WHERE p.party_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, partyId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to fetch party details.", e);
        }
    }

    @Override
    public Optional<Party> findByCode(String code) {
        String sql = BASE_SELECT + " WHERE UPPER(p.party_code) = UPPER(?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to look up party by code.", e);
        }
    }

    @Override
    public Optional<Party> findByName(String name) {
        String sql = BASE_SELECT + " WHERE LOWER(p.party_name) = LOWER(?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to look up party by name.", e);
        }
    }

    @Override
    public long insert(Party party) {
        String sql = "INSERT INTO parties (party_code, party_name, description, logo_path, status) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, party.getPartyCode());
            ps.setString(2, party.getPartyName());
            ps.setString(3, party.getDescription());
            ps.setString(4, party.getLogoPath());
            ps.setString(5, party.getStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    party.setPartyId(id);
                    return id;
                }
                throw new SQLException("Failed to get generated party_id.");
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                throw new DuplicateRecordException("A party with that code or name already exists.", e);
            }
            throw new DatabaseException("Failed to create party.", e);
        }
    }

    @Override
    public void update(Party party) {
        String sql = "UPDATE parties SET party_code = ?, party_name = ?, description = ?, logo_path = ?, status = ? WHERE party_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, party.getPartyCode());
            ps.setString(2, party.getPartyName());
            ps.setString(3, party.getDescription());
            ps.setString(4, party.getLogoPath());
            ps.setString(5, party.getStatus().name());
            ps.setLong(6, party.getPartyId());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                throw new DuplicateRecordException("A party with that code or name already exists.", e);
            }
            throw new DatabaseException("Failed to update party.", e);
        }
    }

    @Override
    public boolean hasRelatedCandidates(long partyId) {
        String sql = "SELECT COUNT(*) FROM candidates WHERE party_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, partyId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to check candidate links for party.", e);
        }
    }

    @Override
    public void delete(long partyId) {
        String sql = "DELETE FROM parties WHERE party_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, partyId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Cannot delete party while candidates are associated with it.", e);
        }
    }

    private Party mapRow(ResultSet rs) throws SQLException {
        Party p = new Party();
        p.setPartyId(rs.getLong("party_id"));
        p.setPartyCode(rs.getString("party_code"));
        p.setPartyName(rs.getString("party_name"));
        p.setDescription(rs.getString("description"));
        p.setLogoPath(rs.getString("logo_path"));
        p.setStatus(PartyStatus.valueOf(rs.getString("status")));
        Timestamp cr = rs.getTimestamp("created_at");
        p.setCreatedAt(cr != null ? cr.toLocalDateTime() : null);
        Timestamp up = rs.getTimestamp("updated_at");
        p.setUpdatedAt(up != null ? up.toLocalDateTime() : null);
        p.setCandidateCount(rs.getLong("candidate_count"));
        return p;
    }
}
