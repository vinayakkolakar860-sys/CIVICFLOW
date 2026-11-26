package com.civicflow.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Constituency-level aggregated simulated election results.
 */
public class ConstituencyResult {
    private long constituencyId;
    private String constituencyCode;
    private String constituencyName;
    private long eligibleVoters;
    private long totalParticipants;
    private long totalBallots;
    private double turnoutPercentage;
    private String leadingCandidateName;
    private List<CandidateResult> candidateResults = new ArrayList<>();
    private List<PartyResult> partyResults = new ArrayList<>();

    public long getConstituencyId() { return constituencyId; }
    public void setConstituencyId(long constituencyId) { this.constituencyId = constituencyId; }

    public String getConstituencyCode() { return constituencyCode; }
    public void setConstituencyCode(String constituencyCode) { this.constituencyCode = constituencyCode; }

    public String getConstituencyName() { return constituencyName; }
    public void setConstituencyName(String constituencyName) { this.constituencyName = constituencyName; }

    public long getEligibleVoters() { return eligibleVoters; }
    public void setEligibleVoters(long eligibleVoters) { this.eligibleVoters = eligibleVoters; }

    public long getTotalParticipants() { return totalParticipants; }
    public void setTotalParticipants(long totalParticipants) { this.totalParticipants = totalParticipants; }

    public long getTotalBallots() { return totalBallots; }
    public void setTotalBallots(long totalBallots) { this.totalBallots = totalBallots; }

    public double getTurnoutPercentage() { return turnoutPercentage; }
    public void setTurnoutPercentage(double turnoutPercentage) { this.turnoutPercentage = turnoutPercentage; }

    public String getLeadingCandidateName() { return leadingCandidateName; }
    public void setLeadingCandidateName(String leadingCandidateName) { this.leadingCandidateName = leadingCandidateName; }

    public List<CandidateResult> getCandidateResults() { return candidateResults; }
    public void setCandidateResults(List<CandidateResult> candidateResults) { this.candidateResults = candidateResults; }

    public List<PartyResult> getPartyResults() { return partyResults; }
    public void setPartyResults(List<PartyResult> partyResults) { this.partyResults = partyResults; }
}
