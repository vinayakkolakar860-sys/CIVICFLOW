package com.civicflow.model;

import java.time.LocalDateTime;

/**
 * Represents a simulated election with lifecycle status and scheduling window.
 */
public class Election {
    private long electionId;
    private String electionCode;
    private String electionName;
    private String description;
    private ElectionType electionType;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private ElectionStatus status;
    private long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Summary counts
    private int constituencyCount;
    private int candidateCount;
    private long totalParticipants;

    public long getElectionId() { return electionId; }
    public void setElectionId(long electionId) { this.electionId = electionId; }

    public String getElectionCode() { return electionCode; }
    public void setElectionCode(String electionCode) { this.electionCode = electionCode; }

    public String getElectionName() { return electionName; }
    public void setElectionName(String electionName) { this.electionName = electionName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ElectionType getElectionType() { return electionType; }
    public void setElectionType(ElectionType electionType) { this.electionType = electionType; }

    public LocalDateTime getStartDateTime() { return startDateTime; }
    public void setStartDateTime(LocalDateTime startDateTime) { this.startDateTime = startDateTime; }

    public LocalDateTime getEndDateTime() { return endDateTime; }
    public void setEndDateTime(LocalDateTime endDateTime) { this.endDateTime = endDateTime; }

    public ElectionStatus getStatus() { return status; }
    public void setStatus(ElectionStatus status) { this.status = status; }

    public long getCreatedBy() { return createdBy; }
    public void setCreatedBy(long createdBy) { this.createdBy = createdBy; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public int getConstituencyCount() { return constituencyCount; }
    public void setConstituencyCount(int constituencyCount) { this.constituencyCount = constituencyCount; }

    public int getCandidateCount() { return candidateCount; }
    public void setCandidateCount(int candidateCount) { this.candidateCount = candidateCount; }

    public long getTotalParticipants() { return totalParticipants; }
    public void setTotalParticipants(long totalParticipants) { this.totalParticipants = totalParticipants; }

    @Override
    public String toString() {
        return electionCode + " — " + electionName + " [" + status + "]";
    }
}
