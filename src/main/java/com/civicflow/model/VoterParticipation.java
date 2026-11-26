package com.civicflow.model;

import java.time.LocalDateTime;

/**
 * Records that a voter participated in a simulated election.
 * Strictly separated from ballot choices — contains NO candidate reference.
 */
public class VoterParticipation {
    private long participationId;
    private long voterId;
    private long electionId;
    private String electionName;
    private String electionCode;
    private LocalDateTime submittedAt;
    private String participationStatus;

    public long getParticipationId() { return participationId; }
    public void setParticipationId(long participationId) { this.participationId = participationId; }

    public long getVoterId() { return voterId; }
    public void setVoterId(long voterId) { this.voterId = voterId; }

    public long getElectionId() { return electionId; }
    public void setElectionId(long electionId) { this.electionId = electionId; }

    public String getElectionName() { return electionName; }
    public void setElectionName(String electionName) { this.electionName = electionName; }

    public String getElectionCode() { return electionCode; }
    public void setElectionCode(String electionCode) { this.electionCode = electionCode; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public String getParticipationStatus() { return participationStatus; }
    public void setParticipationStatus(String participationStatus) { this.participationStatus = participationStatus; }
}
