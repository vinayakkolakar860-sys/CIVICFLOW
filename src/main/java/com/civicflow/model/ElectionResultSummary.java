package com.civicflow.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete aggregated result summary for a simulated election.
 */
public class ElectionResultSummary {
    private Election election;
    private long totalEligibleVoters;
    private long totalParticipants;
    private long totalBallotsCast;
    private double turnoutPercentage;
    private boolean published;
    private List<CandidateResult> candidateResults = new ArrayList<>();
    private List<ConstituencyResult> constituencyResults = new ArrayList<>();
    private List<PartyResult> partyResults = new ArrayList<>();
    private String leadingPartySummary;

    public Election getElection() { return election; }
    public void setElection(Election election) { this.election = election; }

    public long getTotalEligibleVoters() { return totalEligibleVoters; }
    public void setTotalEligibleVoters(long totalEligibleVoters) { this.totalEligibleVoters = totalEligibleVoters; }

    public long getTotalParticipants() { return totalParticipants; }
    public void setTotalParticipants(long totalParticipants) { this.totalParticipants = totalParticipants; }

    public long getTotalBallotsCast() { return totalBallotsCast; }
    public void setTotalBallotsCast(long totalBallotsCast) { this.totalBallotsCast = totalBallotsCast; }

    public double getTurnoutPercentage() { return turnoutPercentage; }
    public void setTurnoutPercentage(double turnoutPercentage) { this.turnoutPercentage = turnoutPercentage; }

    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }

    public List<CandidateResult> getCandidateResults() { return candidateResults; }
    public void setCandidateResults(List<CandidateResult> candidateResults) { this.candidateResults = candidateResults; }

    public List<ConstituencyResult> getConstituencyResults() { return constituencyResults; }
    public void setConstituencyResults(List<ConstituencyResult> constituencyResults) { this.constituencyResults = constituencyResults; }

    public List<PartyResult> getPartyResults() { return partyResults; }
    public void setPartyResults(List<PartyResult> partyResults) { this.partyResults = partyResults; }

    public String getLeadingPartySummary() { return leadingPartySummary; }
    public void setLeadingPartySummary(String leadingPartySummary) { this.leadingPartySummary = leadingPartySummary; }
}
