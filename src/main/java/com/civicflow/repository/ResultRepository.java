package com.civicflow.repository;

import com.civicflow.model.CandidateResult;
import com.civicflow.model.ConstituencyResult;

import java.util.List;

public interface ResultRepository {
    List<CandidateResult> aggregateCandidateResults(long electionId);
    List<ConstituencyResult> aggregateConstituencyStats(long electionId);
    long countEligibleVoters(long electionId);
    long countTotalParticipants(long electionId);
    long countTotalBallots(long electionId);
}
