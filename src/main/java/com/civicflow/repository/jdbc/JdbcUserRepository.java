package com.civicflow.repository.jdbc;

import com.civicflow.config.DatabaseManager;
import com.civicflow.exception.DatabaseException;
import com.civicflow.exception.DuplicateRecordException;
import com.civicflow.model.AccountStatus;
import com.civicflow.model.RoleType;
import com.civicflow.model.User;
import com.civicflow.repository.UserRepository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcUserRepository implements UserRepository {

    private final DatabaseManager db;

    private static final String BASE_SELECT = """
            SELECT u.user_id, u.username, u.password_hash, u.role_id, r.role_name,
                   u.email, u.full_name, u.account_status, u.failed_login_attempts,
                   u.locked_until, u.must_change_password, u.created_at, u.updated_at
            FROM users u
            INNER JOIN roles r ON u.role_id = r.role_id
            """;

    public JdbcUserRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        String sql = BASE_SELECT + " WHERE u.username = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to look up user account.", e);
        }
    }

    @Override
    public Optional<User> findById(long userId) {
        String sql = BASE_SELECT + " WHERE u.user_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to load user by ID.", e);
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String sql = BASE_SELECT + " WHERE LOWER(u.email) = LOWER(?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to look up user by email.", e);
        }
    }

    @Override
    public List<User> findAllAdmins() {
        String sql = BASE_SELECT + " WHERE r.role_name IN ('SUPER_ADMIN', 'ELECTION_ADMIN') ORDER BY r.role_id ASC, u.full_name ASC";
        List<User> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to load administrator accounts.", e);
        }
    }

    @Override
    public int getRoleId(RoleType roleType) {
        String sql = "SELECT role_id FROM roles WHERE role_name = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roleType.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                throw new DatabaseException("Role not found in database: " + roleType.name(), null);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to resolve role identifier.", e);
        }
    }

    @Override
    public long insert(User user) {
        try (Connection conn = db.getConnection()) {
            return insert(conn, user);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                throw new DuplicateRecordException("An account with that username or email already exists.", e);
            }
            throw new DatabaseException("Failed to create user account.", e);
        }
    }

    @Override
    public long insert(Connection conn, User user) throws SQLException {
        String sql = """
                INSERT INTO users (username, password_hash, role_id, email, full_name,
                                   account_status, failed_login_attempts, must_change_password)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setInt(3, user.getRoleId());
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getFullName());
            ps.setString(6, user.getAccountStatus().name());
            ps.setBoolean(7, user.isMustChangePassword());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    user.setUserId(id);
                    return id;
                }
                throw new SQLException("Failed to obtain generated user_id.");
            }
        }
    }

    @Override
    public void updatePassword(long userId, String passwordHash, boolean mustChangePassword) {
        String sql = """
                UPDATE users
                SET password_hash = ?, must_change_password = ?, failed_login_attempts = 0, locked_until = NULL
                WHERE user_id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setBoolean(2, mustChangePassword);
            ps.setLong(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update account password.", e);
        }
    }

    @Override
    public void updateLoginFailure(long userId, int failedAttempts, LocalDateTime lockedUntil, AccountStatus status) {
        String sql = "UPDATE users SET failed_login_attempts = ?, locked_until = ?, account_status = ? WHERE user_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, failedAttempts);
            if (lockedUntil != null) {
                ps.setTimestamp(2, Timestamp.valueOf(lockedUntil));
            } else {
                ps.setNull(2, Types.TIMESTAMP);
            }
            ps.setString(3, status.name());
            ps.setLong(4, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to record login attempt state.", e);
        }
    }

    @Override
    public void resetLoginFailures(long userId) {
        String sql = """
                UPDATE users
                SET failed_login_attempts = 0, locked_until = NULL,
                    account_status = CASE WHEN account_status = 'LOCKED' THEN 'ACTIVE' ELSE account_status END
                WHERE user_id = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to reset login failure counters.", e);
        }
    }

    @Override
    public void updateAccountStatus(long userId, AccountStatus status) {
        try (Connection conn = db.getConnection()) {
            updateAccountStatus(conn, userId, status);
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update user account status.", e);
        }
    }

    @Override
    public void updateAccountStatus(Connection conn, long userId, AccountStatus status) throws SQLException {
        String sql = "UPDATE users SET account_status = ?, locked_until = CASE WHEN ? = 'ACTIVE' THEN NULL ELSE locked_until END WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, status.name());
            ps.setLong(3, userId);
            ps.executeUpdate();
        }
    }

    @Override
    public void updateRole(long userId, RoleType roleType) {
        int roleId = getRoleId(roleType);
        String sql = "UPDATE users SET role_id = ? WHERE user_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to update user role.", e);
        }
    }

    @Override
    public void updateProfile(long userId, String fullName, String email) {
        String sql = "UPDATE users SET full_name = ?, email = ? WHERE user_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setLong(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                throw new DuplicateRecordException("Email address is already in use by another account.", e);
            }
            throw new DatabaseException("Failed to update user profile.", e);
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getLong("user_id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRoleId(rs.getInt("role_id"));
        u.setRoleType(RoleType.valueOf(rs.getString("role_name")));
        u.setEmail(rs.getString("email"));
        u.setFullName(rs.getString("full_name"));
        u.setAccountStatus(AccountStatus.valueOf(rs.getString("account_status")));
        u.setFailedLoginAttempts(rs.getInt("failed_login_attempts"));
        Timestamp locked = rs.getTimestamp("locked_until");
        u.setLockedUntil(locked != null ? locked.toLocalDateTime() : null);
        u.setMustChangePassword(rs.getBoolean("must_change_password"));
        Timestamp created = rs.getTimestamp("created_at");
        u.setCreatedAt(created != null ? created.toLocalDateTime() : null);
        Timestamp updated = rs.getTimestamp("updated_at");
        u.setUpdatedAt(updated != null ? updated.toLocalDateTime() : null);
        return u;
    }
}
