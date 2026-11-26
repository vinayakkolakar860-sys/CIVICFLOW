package com.civicflow.exception;

public class VotingException extends CivicFlowException {
    public VotingException(String userMessage) {
        super("VOTING_ERROR", userMessage);
    }

    public VotingException(String userMessage, Throwable cause) {
        super("VOTING_ERROR", userMessage, cause);
    }
}
