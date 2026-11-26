package com.civicflow.config;

import com.civicflow.exception.DatabaseException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Manages the HikariCP JDBC connection pool and provides explicit transaction execution helpers.
 */
public class DatabaseManager implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseManager.class);

    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute(Connection connection) throws SQLException;
    }

    private final HikariDataSource dataSource;

    public DatabaseManager(AppConfig config) {
        if (config.getDbUrl() == null || config.getDbUrl().isBlank()) {
            throw new DatabaseException("Database URL is not configured. Set CIVICFLOW_DB_URL or config/application.properties.", null);
        }
        if (config.getDbUser() == null || config.getDbUser().isBlank()) {
            throw new DatabaseException("Database username is not configured. Set CIVICFLOW_DB_USER or config/application.properties.", null);
        }

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.getDbUrl());
        hikari.setUsername(config.getDbUser());
        hikari.setPassword(config.getDbPassword());
        hikari.setMaximumPoolSize(config.getMaxPoolSize());
        hikari.setMinimumIdle(config.getMinIdle());
        hikari.setConnectionTimeout(config.getConnectionTimeoutMs());
        hikari.setPoolName("CivicFlow-HikariPool");
        hikari.setAutoCommit(true);
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            this.dataSource = new HikariDataSource(hikari);
            LOGGER.info("Initialized JDBC connection pool targeting {}", sanitizeJdbcUrl(config.getDbUrl()));
        } catch (Exception e) {
            throw new DatabaseException(
                    "Could not connect to the MySQL database. Verify MySQL 8 is running and credentials are configured.", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(3);
        } catch (SQLException e) {
            LOGGER.error("Database connectivity check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Executes work atomically inside a single database transaction with explicit commit/rollback.
     */
    public <T> T executeInTransaction(int isolationLevel, TransactionCallback<T> callback) {
        Connection conn = null;
        boolean originalAutoCommit = true;
        int originalIsolation = Connection.TRANSACTION_REPEATABLE_READ;
        try {
            conn = getConnection();
            originalAutoCommit = conn.getAutoCommit();
            originalIsolation = conn.getTransactionIsolation();
            conn.setTransactionIsolation(isolationLevel);
            conn.setAutoCommit(false);

            T result = callback.execute(conn);
            conn.commit();
            return result;
        } catch (RuntimeException re) {
            rollbackQuietly(conn);
            throw re;
        } catch (SQLException se) {
            rollbackQuietly(conn);
            throw new DatabaseException("Database transaction failed and was safely rolled back.", se);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                    conn.setTransactionIsolation(originalIsolation);
                } catch (SQLException ignored) {
                    // Best-effort reset before returning connection to pool
                }
                try {
                    conn.close();
                } catch (SQLException ignored) {
                    // Closed cleanly
                }
            }
        }
    }

    public <T> T executeInTransaction(TransactionCallback<T> callback) {
        return executeInTransaction(Connection.TRANSACTION_READ_COMMITTED, callback);
    }

    private void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                LOGGER.error("Transaction rollback encountered an error: {}", ex.getMessage());
            }
        }
    }

    private static String sanitizeJdbcUrl(String url) {
        int qIdx = url.indexOf('?');
        return qIdx >= 0 ? url.substring(0, qIdx) : url;
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("JDBC connection pool closed.");
        }
    }
}
