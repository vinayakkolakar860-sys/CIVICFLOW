package com.civicflow.exception;

public class ValidationException extends CivicFlowException {
    public ValidationException(String userMessage) {
        super("VALIDATION_ERROR", userMessage);
    }
}
