package com.civicflow.exception;

public class AuthenticationException extends CivicFlowException {
    public AuthenticationException(String userMessage) {
        super("AUTH_ERROR", userMessage);
    }
}
