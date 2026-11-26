package com.civicflow.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Database-backed KPI and chart statistics for the Admin Dashboard.
 */
public class DashboardStats {
    private long totalVoters;
    private long verifiedVoters;
    private long pendingVoters;
    private long totalCandidates;
    private long totalConstituencies;
    private long totalElections;
    private long upcomingElections;
    private long openElections;
    private long closedElections;
    private long publishedResults;

    private Map<String, Long> electionsByStatus = new LinkedHashMap<>();
    private Map<String, Long> votersByVerificationStatus = new LinkedHashMap<>();
    private Map<String, Long> votersByConstituency = new LinkedHashMap<>();
    private Map<String, Long> candidatesByParty = new LinkedHashMap<>();
    private Map<String, Long> participationByElection = new LinkedHashMap<>();
    private List<AuditLog> recentActivity = List.of();

    public long getTotalVoters() { return totalVoters; }
    public void setTotalVoters(long totalVoters) { this.totalVoters = totalVoters; }

    public long getVerifiedVoters() { return verifiedVoters; }
    public void setVerifiedVoters(long verifiedVoters) { this.verifiedVoters = verifiedVoters; }

    public long getPendingVoters() { return pendingVoters; }
    public void setPendingVoters(long pendingVoters) { this.pendingVoters = pendingVoters; }

    public long getTotalCandidates() { return totalCandidates; }
    public void setTotalCandidates(long totalCandidates) { this.totalCandidates = totalCandidates; }

    public long getTotalConstituencies() { return totalConstituencies; }
    public void setTotalConstituencies(long totalConstituencies) { this.totalConstituencies = totalConstituencies; }

    public long getTotalElections() { return totalElections; }
    public void setTotalElections(long totalElections) { this.totalElections = totalElections; }

    public long getUpcomingElections() { return upcomingElections; }
    public void setUpcomingElections(long upcomingElections) { this.upcomingElections = upcomingElections; }

    public long getOpenElections() { return openElections; }
    public void setOpenElections(long openElections) { this.openElections = openElections; }

    public long getClosedElections() { return closedElections; }
    public void setClosedElections(long closedElections) { this.closedElections = closedElections; }

    public long getPublishedResults() { return publishedResults; }
    public void setPublishedResults(long publishedResults) { this.publishedResults = publishedResults; }

    public Map<String, Long> getElectionsByStatus() { return electionsByStatus; }
    public void setElectionsByStatus(Map<String, Long> electionsByStatus) { this.electionsByStatus = electionsByStatus; }

    public Map<String, Long> getVotersByVerificationStatus() { return votersByVerificationStatus; }
    public void setVotersByVerificationStatus(Map<String, Long> votersByVerificationStatus) { this.votersByVerificationStatus = votersByVerificationStatus; }

    public Map<String, Long> getVotersByConstituency() { return votersByConstituency; }
    public void setVotersByConstituency(Map<String, Long> votersByConstituency) { this.votersByConstituency = votersByConstituency; }

    public Map<String, Long> getCandidatesByParty() { return candidatesByParty; }
    public void setCandidatesByParty(Map<String, Long> candidatesByParty) { this.candidatesByParty = candidatesByParty; }

    public Map<String, Long> getParticipationByElection() { return participationByElection; }
    public void setParticipationByElection(Map<String, Long> participationByElection) { this.participationByElection = participationByElection; }

    public List<AuditLog> getRecentActivity() { return recentActivity; }
    public void setRecentActivity(List<AuditLog> recentActivity) { this.recentActivity = recentActivity; }
}
