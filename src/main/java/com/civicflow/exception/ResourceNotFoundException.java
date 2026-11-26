package com.civicflow.exception;

public class ResourceNotFoundException extends CivicFlowException {
    public ResourceNotFoundException(String userMessage) {
        super("NOT_FOUND", userMessage);
    }
}
