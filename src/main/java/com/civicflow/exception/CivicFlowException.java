package com.civicflow.exception;

/**
 * Base runtime exception for all domain and application errors in CIVICFLOW.
 * Carries a safe user-facing message separate from any internal technical cause.
 */
public class CivicFlowException extends RuntimeException {

    private final String errorCode;

    public CivicFlowException(String errorCode, String userMessage) {
        super(userMessage);
        this.errorCode = errorCode;
    }

    public CivicFlowException(String errorCode, String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return getMessage();
    }
}
