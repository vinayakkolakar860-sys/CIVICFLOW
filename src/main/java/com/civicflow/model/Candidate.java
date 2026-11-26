package com.civicflow.model;

import java.time.LocalDateTime;

/**
 * Represents a fictional candidate participating in simulated elections.
 */
public class Candidate {
    private long candidateId;
    private String candidateCode;
    private String fullName;
    private String profileDescription;
    private int age;
    private String email;
    private String contactNumber;
    private long partyId;
    private String partyName;
    private String partyCode;
    private long constituencyId;
    private String constituencyName;
    private String constituencyCode;
    private String photoPath;
    private CandidateStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public long getCandidateId() { return candidateId; }
    public void setCandidateId(long candidateId) { this.candidateId = candidateId; }

    public String getCandidateCode() { return candidateCode; }
    public void setCandidateCode(String candidateCode) { this.candidateCode = candidateCode; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getProfileDescription() { return profileDescription; }
    public void setProfileDescription(String profileDescription) { this.profileDescription = profileDescription; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }

    public long getPartyId() { return partyId; }
    public void setPartyId(long partyId) { this.partyId = partyId; }

    public String getPartyName() { return partyName; }
    public void setPartyName(String partyName) { this.partyName = partyName; }

    public String getPartyCode() { return partyCode; }
    public void setPartyCode(String partyCode) { this.partyCode = partyCode; }

    public long getConstituencyId() { return constituencyId; }
    public void setConstituencyId(long constituencyId) { this.constituencyId = constituencyId; }

    public String getConstituencyName() { return constituencyName; }
    public void setConstituencyName(String constituencyName) { this.constituencyName = constituencyName; }

    public String getConstituencyCode() { return constituencyCode; }
    public void setConstituencyCode(String constituencyCode) { this.constituencyCode = constituencyCode; }

    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }

    public CandidateStatus getStatus() { return status; }
    public void setStatus(CandidateStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return fullName + " (" + (partyCode != null ? partyCode : "IND") + ")";
    }
}
