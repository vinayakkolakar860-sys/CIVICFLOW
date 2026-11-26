package com.civicflow.model;

/**
 * Aggregated simulated vote tally for a political party across an election or constituency.
 */
public class PartyResult {
    private long partyId;
    private String partyCode;
    private String partyName;
    private long candidateCount;
    private long totalVotes;
    private double votePercentage;
    private boolean leading;
    private boolean tiedForLead;

    public PartyResult() {}

    public PartyResult(long partyId, String partyCode, String partyName, long candidateCount, long totalVotes, double votePercentage) {
        this.partyId = partyId;
        this.partyCode = partyCode;
        this.partyName = partyName;
        this.candidateCount = candidateCount;
        this.totalVotes = totalVotes;
        this.votePercentage = votePercentage;
    }

    public long getPartyId() { return partyId; }
    public void setPartyId(long partyId) { this.partyId = partyId; }

    public String getPartyCode() { return partyCode; }
    public void setPartyCode(String partyCode) { this.partyCode = partyCode; }

    public String getPartyName() { return partyName; }
    public void setPartyName(String partyName) { this.partyName = partyName; }

    public long getCandidateCount() { return candidateCount; }
    public void setCandidateCount(long candidateCount) { this.candidateCount = candidateCount; }

    public long getTotalVotes() { return totalVotes; }
    public void setTotalVotes(long totalVotes) { this.totalVotes = totalVotes; }

    public double getVotePercentage() { return votePercentage; }
    public void setVotePercentage(double votePercentage) { this.votePercentage = votePercentage; }

    public boolean isLeading() { return leading; }
    public void setLeading(boolean leading) { this.leading = leading; }

    public boolean isTiedForLead() { return tiedForLead; }
    public void setTiedForLead(boolean tiedForLead) { this.tiedForLead = tiedForLead; }
}
