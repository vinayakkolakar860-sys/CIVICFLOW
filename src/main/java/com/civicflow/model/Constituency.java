package com.civicflow.model;

import java.time.LocalDateTime;

/**
 * Represents a fictional electoral constituency for simulation purposes.
 */
public class Constituency {
    private long constituencyId;
    private String constituencyCode;
    private String constituencyName;
    private String city;
    private String state;
    private String description;
    private ConstituencyStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Aggregated summary fields (optional)
    private long voterCount;
    private long candidateCount;

    public long getConstituencyId() { return constituencyId; }
    public void setConstituencyId(long constituencyId) { this.constituencyId = constituencyId; }

    public String getConstituencyCode() { return constituencyCode; }
    public void setConstituencyCode(String constituencyCode) { this.constituencyCode = constituencyCode; }

    public String getConstituencyName() { return constituencyName; }
    public void setConstituencyName(String constituencyName) { this.constituencyName = constituencyName; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ConstituencyStatus getStatus() { return status; }
    public void setStatus(ConstituencyStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public long getVoterCount() { return voterCount; }
    public void setVoterCount(long voterCount) { this.voterCount = voterCount; }

    public long getCandidateCount() { return candidateCount; }
    public void setCandidateCount(long candidateCount) { this.candidateCount = candidateCount; }

    @Override
    public String toString() {
        return constituencyCode + " — " + constituencyName;
    }
}
