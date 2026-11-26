package com.civicflow.repository;

import com.civicflow.model.Candidate;
import com.civicflow.model.Constituency;
import com.civicflow.model.Election;
import com.civicflow.model.ElectionStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ElectionRepository {
    List<Election> findAll(String search, ElectionStatus statusFilter);
    List<Election> findByConstituency(long constituencyId);
    List<Election> findEligibleForVoter(long voterId);
    List<Election> findPublishedElections();
    Optional<Election> findById(long electionId);
    Optional<Election> findByCode(String electionCode);
    long insert(Election election);
    void update(Election election);
    void updateStatus(long electionId, ElectionStatus newStatus);
    void assignConstituency(long electionId, long constituencyId);
    void removeConstituency(long electionId, long constituencyId);
    List<Constituency> findAssignedConstituencies(long electionId);
    void assignCandidate(long electionId, long candidateId, long constituencyId);
    void removeCandidate(long electionId, long candidateId);
    List<Candidate> findAssignedCandidates(long electionId, Long constituencyId);
    boolean isCandidateAssigned(long electionId, long candidateId, long constituencyId);
    int syncVoterEligibilityForElection(long electionId);
    long countAll();
    Map<String, Long> countByStatus();
}
