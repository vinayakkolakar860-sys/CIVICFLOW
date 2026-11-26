package com.civicflow.repository;

import com.civicflow.model.VoterParticipation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface VotingRepository {
    boolean isVoterEligible(long electionId, long voterId, long constituencyId);
    boolean hasParticipated(long electionId, long voterId);
    List<VoterParticipation> findParticipationByVoter(long voterId);
    List<VoterParticipation> findParticipationByElection(long electionId);
    Map<String, Long> countParticipationByElection();

    /**
     * Atomically records voter participation (without candidate choice) and an anonymous ballot
     * (without voter identity) within a single database transaction.
     */
    long submitVoteAtomically(long electionId, long voterId, long candidateId, long constituencyId, LocalDateTime submittedAt);
}
