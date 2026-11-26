package com.civicflow.exception;

public class DuplicateRecordException extends CivicFlowException {
    public DuplicateRecordException(String userMessage) {
        super("DUPLICATE_RECORD", userMessage);
    }

    public DuplicateRecordException(String userMessage, Throwable cause) {
        super("DUPLICATE_RECORD", userMessage, cause);
    }
}
