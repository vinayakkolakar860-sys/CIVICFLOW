package com.civicflow.model;

import java.time.LocalDateTime;

/**
 * Represents a fictional political party used exclusively for educational election simulations.
 */
public class Party {
    private long partyId;
    private String partyCode;
    private String partyName;
    private String description;
    private String logoPath;
    private PartyStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long candidateCount;

    public long getPartyId() { return partyId; }
    public void setPartyId(long partyId) { this.partyId = partyId; }

    public String getPartyCode() { return partyCode; }
    public void setPartyCode(String partyCode) { this.partyCode = partyCode; }

    public String getPartyName() { return partyName; }
    public void setPartyName(String partyName) { this.partyName = partyName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }

    public PartyStatus getStatus() { return status; }
    public void setStatus(PartyStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public long getCandidateCount() { return candidateCount; }
    public void setCandidateCount(long candidateCount) { this.candidateCount = candidateCount; }

    @Override
    public String toString() {
        if (partyCode != null && partyCode.equalsIgnoreCase(partyName)) {
            return partyName;
        }
        return partyCode + " — " + partyName;
    }
}
