package com.civicflow.repository;

import com.civicflow.model.Candidate;
import com.civicflow.model.CandidateStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CandidateRepository {
    List<Candidate> search(String query, Long constituencyId, Long partyId, CandidateStatus status);
    Optional<Candidate> findById(long candidateId);
    Optional<Candidate> findByEmail(String email);
    Optional<Candidate> findByCode(String candidateCode);
    long insert(Candidate candidate);
    void update(Candidate candidate);
    void updateStatus(long candidateId, CandidateStatus status);
    boolean isAssignedToProtectedElection(long candidateId);
    void delete(long candidateId);
    long countAll();
    Map<String, Long> countByParty();
}
