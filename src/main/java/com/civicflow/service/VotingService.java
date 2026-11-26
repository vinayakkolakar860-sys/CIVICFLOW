package com.civicflow.service;

import com.civicflow.audit.AuditService;
import com.civicflow.exception.ResourceNotFoundException;
import com.civicflow.exception.VotingException;
import com.civicflow.model.*;
import com.civicflow.repository.ElectionRepository;
import com.civicflow.repository.VoterRepository;
import com.civicflow.repository.VotingRepository;
import com.civicflow.security.AccessControlService;
import com.civicflow.security.UserSession;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Educational Simulated Voting Service.
 *
 * SECURITY & INTEGRITY GUARANTEES:
 * - Only authenticated VOTER accounts may cast ballots (Admins are strictly rejected).
 * - Checks verification status, account status, election status, clock time window, constituency eligibility,
 *   candidate eligibility, and non-duplication.
 * - Persists participation (without candidate_id) and anonymous ballot (without voter_id) atomically
 *   in a single database transaction protected by UNIQUE(voter_id, election_id).
 * - Never logs candidate selection in audit trails or application logs, and omits candidate choice from receipts.
 *
 * DISCLAIMER: This is an educational database simulation, not a cryptographically verifiable public voting system.
 */
public class VotingService {

    private final ElectionRepository electionRepository;
    private final VoterRepository voterRepository;
    private final VotingRepository votingRepository;
    private final AccessControlService accessControl;
    private final AuditService auditService;
    private final Clock clock;

    public VotingService(ElectionRepository electionRepository,
                         VoterRepository voterRepository,
                         VotingRepository votingRepository,
                         AccessControlService accessControl,
                         AuditService auditService,
                         Clock clock) {
        this.electionRepository = electionRepository;
        this.voterRepository = voterRepository;
        this.votingRepository = votingRepository;
        this.accessControl = accessControl;
        this.auditService = auditService;
        this.clock = clock;
    }

    public List<Candidate> getEligibleCandidatesForBallot(long electionId) {
        UserSession session = accessControl.requireVoter();
        Voter voter = voterRepository.findByUserId(session.getUserId())
                .orElseThrow(() -> new VotingException("Voter profile could not be verified."));
        validateVoterCanVoteInElection(electionId, voter);
        return electionRepository.findAssignedCandidates(electionId, voter.getConstituencyId());
    }

    public boolean hasCurrentVoterParticipated(long electionId) {
        UserSession session = accessControl.requireVoter();
        return votingRepository.hasParticipated(electionId, session.getVoterProfile().getVoterId());
    }

    /**
     * Casts a simulated ballot for the authenticated voter inside a single database transaction.
     */
    public VotingReceipt castSimulatedBallot(long electionId, long candidateId) {
        UserSession session = accessControl.requireVoter();
        Voter voter = voterRepository.findByUserId(session.getUserId())
                .orElseThrow(() -> new VotingException("Voter profile could not be verified."));

        Election election = validateVoterCanVoteInElection(electionId, voter);

        // Verify candidate is legitimately assigned to this election & voter's constituency
        if (!electionRepository.isCandidateAssigned(electionId, candidateId, voter.getConstituencyId())) {
            auditService.record("VOTING", "BALLOT_REJECTED_INVALID_CANDIDATE",
                    "Invalid candidate selection attempted for election " + election.getElectionCode(),
                    AuditResultStatus.DENIED);
            throw new VotingException("Selected candidate is not eligible for your constituency in this election.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        long participationId = votingRepository.submitVoteAtomically(
                electionId, voter.getVoterId(), candidateId, voter.getConstituencyId(), now);

        // Audit records participation event ONLY — NEVER logs candidateId or ballot choice!
        auditService.record("VOTING", "SIMULATED_PARTICIPATION_RECORDED",
                "Recorded simulated voting participation for election " + election.getElectionCode()
                        + " (participationRef=" + participationId + ")",
                AuditResultStatus.SUCCESS);

        String receiptCode = "CF-RCPT-" + electionId + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new VotingReceipt(
                receiptCode,
                election.getElectionId(),
                election.getElectionName(),
                now,
                "Your simulated participation has been recorded anonymously. "
                        + "In accordance with simulated ballot privacy rules, your candidate selection is not shown on this receipt."
        );
    }

    private Election validateVoterCanVoteInElection(long electionId, Voter voter) {
        if (voter.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new VotingException("Your voter account is not active and cannot participate.");
        }
        if (voter.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new VotingException("Your voter registration is " + voter.getVerificationStatus()
                    + ". Only APPROVED voters may cast simulated ballots.");
        }

        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new ResourceNotFoundException("Election not found."));

        if (election.getStatus() != ElectionStatus.OPEN) {
            throw new VotingException("Voting is not open for this election (current status: " + election.getStatus() + ").");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(election.getStartDateTime())) {
            throw new VotingException("Voting has not started yet for this election.");
        }
        if (now.isAfter(election.getEndDateTime())) {
            throw new VotingException("The voting window for this election has closed.");
        }

        if (!votingRepository.isVoterEligible(electionId, voter.getVoterId(), voter.getConstituencyId())) {
            throw new VotingException("You are not listed as an eligible voter for this election's assigned constituencies.");
        }

        if (votingRepository.hasParticipated(electionId, voter.getVoterId())) {
            throw new VotingException("Duplicate vote rejected: you have already participated in this election.");
        }

        return election;
    }
}
