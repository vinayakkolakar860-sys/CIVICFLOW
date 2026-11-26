package com.civicflow.model;

import java.time.LocalDateTime;

/**
 * Confirmation receipt returned after casting a simulated ballot.
 * Deliberately excludes candidate selection to preserve simulated ballot secrecy.
 */
public class VotingReceipt {
    private final String confirmationCode;
    private final long electionId;
    private final String electionName;
    private final LocalDateTime submittedAt;
    private final String message;

    public VotingReceipt(String confirmationCode, long electionId, String electionName,
                         LocalDateTime submittedAt, String message) {
        this.confirmationCode = confirmationCode;
        this.electionId = electionId;
        this.electionName = electionName;
        this.submittedAt = submittedAt;
        this.message = message;
    }

    public String getConfirmationCode() { return confirmationCode; }
    public long getElectionId() { return electionId; }
    public String getElectionName() { return electionName; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public String getMessage() { return message; }
}
