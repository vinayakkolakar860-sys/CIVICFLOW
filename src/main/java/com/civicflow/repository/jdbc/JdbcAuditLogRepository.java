package com.civicflow.repository.jdbc;

import com.civicflow.config.DatabaseManager;
import com.civicflow.exception.DatabaseException;
import com.civicflow.model.AuditLog;
import com.civicflow.model.AuditResultStatus;
import com.civicflow.model.PageResult;
import com.civicflow.repository.AuditLogRepository;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class JdbcAuditLogRepository implements AuditLogRepository {

    private final DatabaseManager db;

    public JdbcAuditLogRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void insert(AuditLog log) {
        String sql = """
                INSERT INTO audit_logs (user_id, username, action, module, action_timestamp, details, result_status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (log.getUserId() != null) {
                ps.setLong(1, log.getUserId());
            } else {
                ps.setNull(1, Types.BIGINT);
            }
            ps.setString(2, log.getUsername());
            ps.setString(3, log.getAction());
            ps.setString(4, log.getModule());
            ps.setTimestamp(5, Timestamp.valueOf(log.getTimestamp()));
            ps.setString(6, log.getDetails());
            ps.setString(7, log.getResultStatus().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to persist audit log entry.", e);
        }
    }

    @Override
    public PageResult<AuditLog> search(String module, String searchText, LocalDate fromDate, LocalDate toDate,
                                       int pageNumber, int pageSize) {
        int safePage = Math.max(1, pageNumber);
        int safeSize = Math.max(1, Math.min(200, pageSize));
        int offset = (safePage - 1) * safeSize;

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendFilters(where, params, module, searchText, fromDate, toDate);

        String countSql = "SELECT COUNT(*) FROM audit_logs " + where;
        String dataSql = "SELECT audit_id, user_id, username, action, module, action_timestamp, details, result_status FROM audit_logs "
                + where + " ORDER BY action_timestamp DESC, audit_id DESC LIMIT ? OFFSET ?";

        try (Connection conn = db.getConnection()) {
            long total = 0;
            try (PreparedStatement cps = conn.prepareStatement(countSql)) {
                setParams(cps, params);
                try (ResultSet rs = cps.executeQuery()) {
                    if (rs.next()) total = rs.getLong(1);
                }
            }
            List<AuditLog> items = new ArrayList<>();
            try (PreparedStatement dps = conn.prepareStatement(dataSql)) {
                int idx = setParams(dps, params);
                dps.setInt(idx++, safeSize);
                dps.setInt(idx, offset);
                try (ResultSet rs = dps.executeQuery()) {
                    while (rs.next()) {
                        items.add(mapRow(rs));
                    }
                }
            }
            return new PageResult<>(items, total, safePage, safeSize);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to query audit logs.", e);
        }
    }

    @Override
    public List<AuditLog> findAllFiltered(String module, String searchText, LocalDate fromDate, LocalDate toDate, int limit) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendFilters(where, params, module, searchText, fromDate, toDate);
        String sql = "SELECT audit_id, user_id, username, action, module, action_timestamp, details, result_status FROM audit_logs "
                + where + " ORDER BY action_timestamp DESC, audit_id DESC LIMIT ?";

        List<AuditLog> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = setParams(ps, params);
            ps.setInt(idx, Math.max(1, Math.min(5000, limit)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to retrieve audit logs.", e);
        }
    }

    @Override
    public List<AuditLog> findRecent(int limit) {
        return findAllFiltered(null, null, null, null, limit);
    }

    private void appendFilters(StringBuilder where, List<Object> params, String module, String searchText,
                               LocalDate fromDate, LocalDate toDate) {
        if (module != null && !module.isBlank() && !"ALL".equalsIgnoreCase(module)) {
            where.append(" AND module = ? ");
            params.add(module.trim().toUpperCase());
        }
        if (searchText != null && !searchText.isBlank()) {
            where.append(" AND (LOWER(COALESCE(username,'')) LIKE ? OR LOWER(action) LIKE ? OR LOWER(COALESCE(details,'')) LIKE ?) ");
            String like = "%" + searchText.trim().toLowerCase() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (fromDate != null) {
            where.append(" AND action_timestamp >= ? ");
            params.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            where.append(" AND action_timestamp < ? ");
            params.add(Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
        }
    }

    private int setParams(PreparedStatement ps, List<Object> params) throws SQLException {
        int idx = 1;
        for (Object p : params) {
            ps.setObject(idx++, p);
        }
        return idx;
    }

    private AuditLog mapRow(ResultSet rs) throws SQLException {
        AuditLog log = new AuditLog();
        log.setAuditId(rs.getLong("audit_id"));
        long uid = rs.getLong("user_id");
        log.setUserId(rs.wasNull() ? null : uid);
        log.setUsername(rs.getString("username"));
        log.setAction(rs.getString("action"));
        log.setModule(rs.getString("module"));
        Timestamp ts = rs.getTimestamp("action_timestamp");
        log.setTimestamp(ts != null ? ts.toLocalDateTime() : null);
        log.setDetails(rs.getString("details"));
        log.setResultStatus(AuditResultStatus.valueOf(rs.getString("result_status")));
        return log;
    }
}
