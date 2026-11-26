package com.civicflow.repository.jdbc;

import com.civicflow.config.DatabaseManager;
import com.civicflow.exception.DatabaseException;
import com.civicflow.exception.DuplicateRecordException;
import com.civicflow.model.*;
import com.civicflow.repository.VoterRepository;

import java.sql.*;
import java.util.*;

public class JdbcVoterRepository implements VoterRepository {

    private final DatabaseManager db;

    private static final String BASE_SELECT = """
            SELECT v.voter_id, v.voter_code, v.user_id, u.username, v.first_name, v.last_name,
                   v.email, v.phone_number, v.date_of_birth, v.address, v.city, v.state,
                   v.constituency_id, c.constituency_name, c.constituency_code,
                   v.registration_date, v.account_status, v.verification_status,
                   v.created_at, v.updated_at
            FROM voters v
            INNER JOIN users u ON v.user_id = u.user_id
            INNER JOIN constituencies c ON v.constituency_id = c.constituency_id
            """;

    public JdbcVoterRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public PageResult<Voter> search(String query, VerificationStatus verificationStatus, AccountStatus accountStatus,
                                    Long constituencyId, int pageNumber, int pageSize) {
        int safePage = Math.max(1, pageNumber);
        int safeSize = Math.max(1, Math.min(200, pageSize));
        int offset = (safePage - 1) * safeSize;

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendFilters(where, params, query, verificationStatus, accountStatus, constituencyId);

        String countSql = "SELECT COUNT(*) FROM voters v INNER JOIN users u ON v.user_id = u.user_id " + where;
        String dataSql = BASE_SELECT + where + " ORDER BY v.created_at DESC, v.voter_id DESC LIMIT ? OFFSET ?";

        try (Connection conn = db.getConnection()) {
            long total = 0;
            try (PreparedStatement countPs = conn.prepareStatement(countSql)) {
                setParams(countPs, params);
                try (ResultSet rs = countPs.executeQuery()) {
                    if (rs.next()) total = rs.getLong(1);
                }
            }

            List<Voter> items = new ArrayList<>();
            try (PreparedStatement dataPs = conn.prepareStatement(dataSql)) {
                int idx = setParams(dataPs, params);
                dataPs.setInt(idx++, safeSize);
                dataPs.setInt(idx, offset);
                try (ResultSet rs = dataPs.executeQuery()) {
                    while (rs.next()) {
                        items.add(mapRow(rs));
                    }
                }
            }
            return new PageResult<>(items, total, safePage, safeSize);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to search voter records.", e);
        }
    }

    @Override
    public List<Voter> findAll(String query, VerificationStatus verificationStatus, AccountStatus accountStatus, Long constituencyId) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendFilters(where, params, query, verificationStatus, accountStatus, constituencyId);
        String sql = BASE_SELECT + where + " ORDER BY v.last_name ASC, v.first_name ASC";

        List<Voter> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to retrieve voters.", e);
        }
    }

    @Override
    public Optional<Voter> findById(long voterId) {
        String sql = BASE_SELECT + " WHERE v.voter_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, voterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to fetch voter details.", e);
        }
    }

    @Override
    public Optional<Voter> findByUserId(long userId) {
        String sql = BASE_SELECT + " WHERE v.user_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to fetch voter profile for user.", e);
        }
    }

    @Override
    public Optional<Voter> findByEmail(String email) {
        String sql = BASE_SELECT + " WHERE LOWER(v.email) = LOWER(?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to look up voter by email.", e);
        }
    }

    @Override
    public Optional<Voter> findByCode(String voterCode) {
        String sql = BASE_SELECT + " WHERE UPPER(v.voter_code) = UPPER(?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, voterCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to look up voter by code.", e);
        }
    }

    @Override
    public long insert(Connection conn, Voter voter) throws SQLException {
        String sql = """
                INSERT INTO voters (voter_code, user_id, first_name, last_name, email, phone_number,
                                    date_of_birth, address, city, state, constituency_id,
                                    registration_date, account_status, verification_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, voter.getVoterCode());
            ps.setLong(2, voter.getUserId());
            ps.setString(3, voter.getFirstName());
            ps.setString(4, voter.getLastName());
            ps.setString(5, voter.getEmail());
            ps.setString(6, voter.getPhoneNumber());
            ps.setDate(7, java.sql.Date.valueOf(voter.getDateOfBirth()));
            ps.setString(8, voter.getAddress());
            ps.setString(9, voter.getCity());
            ps.setString(10, voter.getState());
            ps.setLong(11, voter.getConstituencyId());
            ps.setDate(12, java.sql.Date.valueOf(voter.getRegistrationDate()));
            ps.setString(13, voter.getAccountStatus().name());
            ps.setString(14, voter.getVerificationStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    voter.setVoterId(id);
                    return id;
                }
                throw new SQLException("Failed to get generated voter_id.");
            }
        }
    }

    @Override
    public void update(Voter voter) {
        String sql = """
                UPDATE voters
                SET first_name = ?, last_name = ?, email = ?, phone_number = ?,
                    date_of_birth = ?, address = ?, city = ?, state = ?, constituency_id = ?
                WHERE voter_id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, voter.getFirstName());
            ps.setString(2, voter.getLastName());
            ps.setString(3, voter.getEmail());
            ps.setString(4, voter.getPhoneNumber());
            ps.setDate(5, java.sql.Date.valueOf(voter.getDateOfBirth()));
            ps.setString(6, voter.getAddress());
            ps.setString(7, voter.getCity());
            ps.setString(8, voter.getState());
            ps.setLong(9, voter.getConstituencyId());
            ps.setLong(10, voter.getVoterId());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                throw new DuplicateRecordException("A voter with that email address already exists.", e);
            }
            throw new DatabaseException("Failed to update voter details.", e);
        }
    }

    @Override
    public void updateVerificationStatus(long voterId, VerificationStatus status) {
        String sql = "UPDATE voters SET verification_status = ? WHERE voter_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, voterId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update voter verification status.", e);
        }
    }

    @Override
    public void updateAccountStatus(Connection conn, long voterId, AccountStatus status) throws SQLException {
        String sql = "UPDATE voters SET account_status = ? WHERE voter_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, voterId);
            ps.executeUpdate();
        }
    }

    @Override
    public long countAll() {
        return countQuery("SELECT COUNT(*) FROM voters", null);
    }

    @Override
    public long countByVerificationStatus(VerificationStatus status) {
        return countQuery("SELECT COUNT(*) FROM voters WHERE verification_status = ?", status.name());
    }

    @Override
    public Map<String, Long> countByVerificationStatusGrouped() {
        String sql = "SELECT verification_status, COUNT(*) AS cnt FROM voters GROUP BY verification_status";
        Map<String, Long> map = new LinkedHashMap<>();
        for (VerificationStatus s : VerificationStatus.values()) {
            map.put(s.name(), 0L);
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("verification_status"), rs.getLong("cnt"));
            }
            return map;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to aggregate voter verification counts.", e);
        }
    }

    @Override
    public Map<String, Long> countByConstituency() {
        String sql = """
                SELECT c.constituency_name, COUNT(v.voter_id) AS cnt
                FROM constituencies c
                LEFT JOIN voters v ON v.constituency_id = c.constituency_id
                GROUP BY c.constituency_id, c.constituency_name
                ORDER BY cnt DESC, c.constituency_name ASC
                """;
        Map<String, Long> map = new LinkedHashMap<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("constituency_name"), rs.getLong("cnt"));
            }
            return map;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to aggregate voters by constituency.", e);
        }
    }

    private long countQuery(String sql, String param) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param != null) ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to count voter records.", e);
        }
    }

    private void appendFilters(StringBuilder where, List<Object> params, String query,
                               VerificationStatus verificationStatus, AccountStatus accountStatus, Long constituencyId) {
        if (query != null && !query.isBlank()) {
            where.append(" AND (LOWER(v.voter_code) LIKE ? OR LOWER(v.first_name) LIKE ? OR LOWER(v.last_name) LIKE ? OR LOWER(v.email) LIKE ? OR LOWER(u.username) LIKE ?) ");
            String like = "%" + query.trim().toLowerCase() + "%";
            for (int i = 0; i < 5; i++) params.add(like);
        }
        if (verificationStatus != null) {
            where.append(" AND v.verification_status = ? ");
            params.add(verificationStatus.name());
        }
        if (accountStatus != null) {
            where.append(" AND v.account_status = ? ");
            params.add(accountStatus.name());
        }
        if (constituencyId != null) {
            where.append(" AND v.constituency_id = ? ");
            params.add(constituencyId);
        }
    }

    private int setParams(PreparedStatement ps, List<Object> params) throws SQLException {
        int i = 1;
        for (Object p : params) {
            ps.setObject(i++, p);
        }
        return i;
    }

    private Voter mapRow(ResultSet rs) throws SQLException {
        Voter v = new Voter();
        v.setVoterId(rs.getLong("voter_id"));
        v.setVoterCode(rs.getString("voter_code"));
        v.setUserId(rs.getLong("user_id"));
        v.setUsername(rs.getString("username"));
        v.setFirstName(rs.getString("first_name"));
        v.setLastName(rs.getString("last_name"));
        v.setEmail(rs.getString("email"));
        v.setPhoneNumber(rs.getString("phone_number"));
        java.sql.Date dob = rs.getDate("date_of_birth");
        v.setDateOfBirth(dob != null ? dob.toLocalDate() : null);
        v.setAddress(rs.getString("address"));
        v.setCity(rs.getString("city"));
        v.setState(rs.getString("state"));
        v.setConstituencyId(rs.getLong("constituency_id"));
        v.setConstituencyName(rs.getString("constituency_name"));
        v.setConstituencyCode(rs.getString("constituency_code"));
        java.sql.Date reg = rs.getDate("registration_date");
        v.setRegistrationDate(reg != null ? reg.toLocalDate() : null);
        v.setAccountStatus(AccountStatus.valueOf(rs.getString("account_status")));
        v.setVerificationStatus(VerificationStatus.valueOf(rs.getString("verification_status")));
        Timestamp cr = rs.getTimestamp("created_at");
        v.setCreatedAt(cr != null ? cr.toLocalDateTime() : null);
        Timestamp up = rs.getTimestamp("updated_at");
        v.setUpdatedAt(up != null ? up.toLocalDateTime() : null);
        return v;
    }
}
