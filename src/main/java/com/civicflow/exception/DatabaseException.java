package com.civicflow.exception;

public class DatabaseException extends CivicFlowException {
    public DatabaseException(String userMessage) {
        super("DATABASE_ERROR", userMessage);
    }

    public DatabaseException(String userMessage, Throwable cause) {
        super("DATABASE_ERROR", userMessage, cause);
    }
}
