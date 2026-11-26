package com.civicflow.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * Centralized error mapping and safe logging utility.
 * Ensures raw SQL queries, stack traces, passwords, or ballot details are never exposed to users.
 */
public final class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private GlobalExceptionHandler() {
    }

    /**
     * Converts any throwable into a safe, human-readable message while logging technical context.
     */
    public static String handleAndGetUserMessage(Throwable throwable, String operationContext) {
        Throwable root = unwrap(throwable);

        if (root instanceof CivicFlowException cfe) {
            if (cfe instanceof DatabaseException) {
                LOGGER.error("Database failure during {}: {}", operationContext, cfe.getMessage(), cfe.getCause());
            } else {
                LOGGER.warn("Application error [{}] during {}: {}", cfe.getErrorCode(), operationContext, cfe.getMessage());
            }
            return cfe.getUserMessage();
        }

        if (root instanceof SQLException sqlEx) {
            LOGGER.error("SQL error [state={}, code={}] during {}", sqlEx.getSQLState(), sqlEx.getErrorCode(), operationContext, sqlEx);
            return mapSqlErrorMessage(sqlEx);
        }

        LOGGER.error("Unexpected error during {}", operationContext, root);
        return "An unexpected error occurred while processing your request. Please check the application logs or contact an administrator.";
    }

    public static String mapSqlErrorMessage(SQLException ex) {
        int code = ex.getErrorCode();
        String state = ex.getSQLState() != null ? ex.getSQLState() : "";
        if (code == 1062 || "23000".equals(state)) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("foreign key")) {
                return "This operation cannot be completed because related records depend on or reference this item.";
            }
            return "A record with the same unique identifier already exists.";
        }
        if (code == 1451 || code == 1452) {
            return "This record is referenced by other election records and cannot be modified or deleted in this way.";
        }
        if (state.startsWith("08")) {
            return "Unable to communicate with the MySQL database. Verify that MySQL is running and credentials are configured correctly.";
        }
        return "A database error prevented completing this operation. Please try again.";
    }

    private static Throwable unwrap(Throwable t) {
        Throwable current = t;
        while (current != null && current.getCause() != null
                && !(current instanceof CivicFlowException)
                && !(current instanceof SQLException)
                && (current.getClass().getName().contains("ExecutionException")
                    || current.getClass().getName().contains("CompletionException"))) {
            current = current.getCause();
        }
        return current != null ? current : t;
    }
}
