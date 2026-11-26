package com.civicflow.model;

/**
 * Aggregated simulated vote tally for an individual candidate in an election/constituency.
 */
public class CandidateResult {
    private long candidateId;
    private String candidateCode;
    private String candidateName;
    private String partyCode;
    private String partyName;
    private long constituencyId;
    private String constituencyCode;
    private String constituencyName;
    private long voteCount;
    private double percentage;

    public long getCandidateId() { return candidateId; }
    public void setCandidateId(long candidateId) { this.candidateId = candidateId; }

    public String getCandidateCode() { return candidateCode; }
    public void setCandidateCode(String candidateCode) { this.candidateCode = candidateCode; }

    public String getCandidateName() { return candidateName; }
    public void setCandidateName(String candidateName) { this.candidateName = candidateName; }

    public String getPartyCode() { return partyCode; }
    public void setPartyCode(String partyCode) { this.partyCode = partyCode; }

    public String getPartyName() { return partyName; }
    public void setPartyName(String partyName) { this.partyName = partyName; }

    public long getConstituencyId() { return constituencyId; }
    public void setConstituencyId(long constituencyId) { this.constituencyId = constituencyId; }

    public String getConstituencyCode() { return constituencyCode; }
    public void setConstituencyCode(String constituencyCode) { this.constituencyCode = constituencyCode; }

    public String getConstituencyName() { return constituencyName; }
    public void setConstituencyName(String constituencyName) { this.constituencyName = constituencyName; }

    public long getVoteCount() { return voteCount; }
    public void setVoteCount(long voteCount) { this.voteCount = voteCount; }

    public double getPercentage() { return percentage; }
    public void setPercentage(double percentage) { this.percentage = percentage; }
}
