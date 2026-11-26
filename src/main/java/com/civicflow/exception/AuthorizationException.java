package com.civicflow.exception;

public class AuthorizationException extends CivicFlowException {
    public AuthorizationException(String userMessage) {
        super("ACCESS_DENIED", userMessage);
    }
}
