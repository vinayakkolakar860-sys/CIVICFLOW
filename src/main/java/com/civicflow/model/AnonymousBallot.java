package com.civicflow.model;

import java.time.LocalDateTime;

/**
 * Represents an anonymous simulated ballot record.
 * Strictly contains NO voter identifier to maintain database-level separation from voter participation.
 */
public class AnonymousBallot {
    private long ballotId;
    private long electionId;
    private long candidateId;
    private long constituencyId;
    private LocalDateTime submittedAt;

    public long getBallotId() { return ballotId; }
    public void setBallotId(long ballotId) { this.ballotId = ballotId; }

    public long getElectionId() { return electionId; }
    public void setElectionId(long electionId) { this.electionId = electionId; }

    public long getCandidateId() { return candidateId; }
    public void setCandidateId(long candidateId) { this.candidateId = candidateId; }

    public long getConstituencyId() { return constituencyId; }
    public void setConstituencyId(long constituencyId) { this.constituencyId = constituencyId; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
