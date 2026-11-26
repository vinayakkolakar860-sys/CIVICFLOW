package com.civicflow.repository;

import com.civicflow.model.AccountStatus;
import com.civicflow.model.RoleType;
import com.civicflow.model.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username);
    Optional<User> findById(long userId);
    Optional<User> findByEmail(String email);
    List<User> findAllAdmins();
    int getRoleId(RoleType roleType);
    long insert(User user);
    long insert(Connection conn, User user) throws SQLException;
    void updatePassword(long userId, String passwordHash, boolean mustChangePassword);
    void updateLoginFailure(long userId, int failedAttempts, LocalDateTime lockedUntil, AccountStatus status);
    void resetLoginFailures(long userId);
    void updateAccountStatus(long userId, AccountStatus status);
    void updateAccountStatus(Connection conn, long userId, AccountStatus status) throws SQLException;
    void updateRole(long userId, RoleType roleType);
    void updateProfile(long userId, String fullName, String email);
}
