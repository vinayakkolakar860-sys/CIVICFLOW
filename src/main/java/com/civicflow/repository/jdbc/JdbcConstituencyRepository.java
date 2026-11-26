package com.civicflow.repository.jdbc;

import com.civicflow.config.DatabaseManager;
import com.civicflow.exception.DatabaseException;
import com.civicflow.exception.DuplicateRecordException;
import com.civicflow.model.Constituency;
import com.civicflow.model.ConstituencyStatus;
import com.civicflow.repository.ConstituencyRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcConstituencyRepository implements ConstituencyRepository {

    private final DatabaseManager db;

    private static final String SELECT_WITH_COUNTS = """
            SELECT c.constituency_id, c.constituency_code, c.constituency_name, c.city, c.state,
                   c.description, c.status, c.created_at, c.updated_at,
                   (SELECT COUNT(*) FROM voters v WHERE v.constituency_id = c.constituency_id) AS voter_count,
                   (SELECT COUNT(*) FROM candidates ca WHERE ca.constituency_id = c.constituency_id) AS candidate_count
            FROM constituencies c
            """;

    public JdbcConstituencyRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public List<Constituency> findAll(String search, ConstituencyStatus statusFilter) {
        StringBuilder sql = new StringBuilder(SELECT_WITH_COUNTS).append(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(c.constituency_code) LIKE ? OR LOWER(c.constituency_name) LIKE ? OR LOWER(c.city) LIKE ? OR LOWER(c.state) LIKE ?) ");
            String like = "%" + search.trim().toLowerCase() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (statusFilter != null) {
            sql.append(" AND c.status = ? ");
            params.add(statusFilter.name());
        }
        sql.append(" ORDER BY c.constituency_code ASC");

        List<Constituency> results = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
            return results;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to fetch constituencies.", e);
        }
    }

    @Override
    public Optional<Constituency> findById(long constituencyId) {
        String sql = SELECT_WITH_COUNTS + " WHERE c.constituency_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, constituencyId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to fetch constituency details.", e);
        }
    }

    @Override
    public Optional<Constituency> findByCode(String code) {
        String sql = SELECT_WITH_COUNTS + " WHERE UPPER(c.constituency_code) = UPPER(?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to look up constituency by code.", e);
        }
    }

    @Override
    public long insert(Constituency c) {
        String sql = """
                INSERT INTO constituencies (constituency_code, constituency_name, city, state, description, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getConstituencyCode());
            ps.setString(2, c.getConstituencyName());
            ps.setString(3, c.getCity());
            ps.setString(4, c.getState());
            ps.setString(5, c.getDescription());
            ps.setString(6, c.getStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    c.setConstituencyId(id);
                    return id;
                }
                throw new SQLException("No generated key returned for constituency.");
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                throw new DuplicateRecordException("Constituency code '" + c.getConstituencyCode() + "' already exists.", e);
            }
            throw new DatabaseException("Failed to add constituency.", e);
        }
    }

    @Override
    public void update(Constituency c) {
        String sql = """
                UPDATE constituencies
                SET constituency_code = ?, constituency_name = ?, city = ?, state = ?, description = ?, status = ?
                WHERE constituency_id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getConstituencyCode());
            ps.setString(2, c.getConstituencyName());
            ps.setString(3, c.getCity());
            ps.setString(4, c.getState());
            ps.setString(5, c.getDescription());
            ps.setString(6, c.getStatus().name());
            ps.setLong(7, c.getConstituencyId());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                throw new DuplicateRecordException("Constituency code '" + c.getConstituencyCode() + "' already exists.", e);
            }
            throw new DatabaseException("Failed to update constituency.", e);
        }
    }

    @Override
    public boolean hasRelatedRecords(long constituencyId) {
        String sql = """
                SELECT (
                    (SELECT COUNT(*) FROM voters WHERE constituency_id = ?) +
                    (SELECT COUNT(*) FROM candidates WHERE constituency_id = ?) +
                    (SELECT COUNT(*) FROM election_constituencies WHERE constituency_id = ?) +
                    (SELECT COUNT(*) FROM anonymous_ballots WHERE constituency_id = ?)
                ) AS total_refs
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, constituencyId);
            ps.setLong(2, constituencyId);
            ps.setLong(3, constituencyId);
            ps.setLong(4, constituencyId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong("total_refs") > 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to check related records for constituency.", e);
        }
    }

    @Override
    public void delete(long constituencyId) {
        String sql = "DELETE FROM constituencies WHERE constituency_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, constituencyId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Cannot delete constituency because related records reference it.", e);
        }
    }

    @Override
    public long countAll() {
        String sql = "SELECT COUNT(*) FROM constituencies";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to count constituencies.", e);
        }
    }

    private Constituency mapRow(ResultSet rs) throws SQLException {
        Constituency c = new Constituency();
        c.setConstituencyId(rs.getLong("constituency_id"));
        c.setConstituencyCode(rs.getString("constituency_code"));
        c.setConstituencyName(rs.getString("constituency_name"));
        c.setCity(rs.getString("city"));
        c.setState(rs.getString("state"));
        c.setDescription(rs.getString("description"));
        c.setStatus(ConstituencyStatus.valueOf(rs.getString("status")));
        Timestamp cr = rs.getTimestamp("created_at");
        c.setCreatedAt(cr != null ? cr.toLocalDateTime() : null);
        Timestamp up = rs.getTimestamp("updated_at");
        c.setUpdatedAt(up != null ? up.toLocalDateTime() : null);
        c.setVoterCount(rs.getLong("voter_count"));
        c.setCandidateCount(rs.getLong("candidate_count"));
        return c;
    }
}
