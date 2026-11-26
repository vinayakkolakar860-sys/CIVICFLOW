package com.civicflow.service;

import com.civicflow.audit.AuditService;
import com.civicflow.exception.AuthorizationException;
import com.civicflow.exception.VotingException;
import com.civicflow.model.*;
import com.civicflow.repository.ElectionRepository;
import com.civicflow.repository.VoterRepository;
import com.civicflow.repository.VotingRepository;
import com.civicflow.security.AccessControlService;
import com.civicflow.security.SessionManager;
import com.civicflow.security.UserSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VotingServiceTest {

    @Mock private ElectionRepository electionRepository;
    @Mock private VoterRepository voterRepository;
    @Mock private VotingRepository votingRepository;
    @Mock private AuditService auditService;

    private SessionManager sessionManager;
    private VotingService votingService;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
        AccessControlService ac = new AccessControlService(sessionManager);
        fixedClock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC);
        votingService = new VotingService(electionRepository, voterRepository, votingRepository, ac, auditService, fixedClock);
    }

    @Test
    void eligibleVerifiedVoterSubmitsSimulatedBallotAndReceiptOmitsCandidate() {
        Voter voter = loginVoter(100L, 5L, VerificationStatus.APPROVED, AccountStatus.ACTIVE);
        Election open = electionWithWindow(10L, ElectionStatus.OPEN,
                LocalDateTime.of(2026, 6, 1, 8, 0), LocalDateTime.of(2026, 6, 20, 18, 0));

        when(voterRepository.findByUserId(50L)).thenReturn(Optional.of(voter));
        when(electionRepository.findById(10L)).thenReturn(Optional.of(open));
        when(votingRepository.isVoterEligible(10L, 100L, 5L)).thenReturn(true);
        when(votingRepository.hasParticipated(10L, 100L)).thenReturn(false);
        when(electionRepository.isCandidateAssigned(10L, 77L, 5L)).thenReturn(true);
        when(votingRepository.submitVoteAtomically(eq(10L), eq(100L), eq(77L), eq(5L), any())).thenReturn(999L);

        VotingReceipt receipt = votingService.castSimulatedBallot(10L, 77L);

        assertNotNull(receipt);
        assertEquals(10L, receipt.getElectionId());
        assertFalse(receipt.getMessage().contains("77"));

        // Verify audit details never include candidateId
        ArgumentCaptor<String> detailsCap = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(eq("VOTING"), eq("SIMULATED_PARTICIPATION_RECORDED"), detailsCap.capture(), eq(AuditResultStatus.SUCCESS));
        assertFalse(detailsCap.getValue().contains("77"));
    }

    @Test
    void adminCannotCastVoterBallot() {
        User admin = new User();
        admin.setUserId(1L);
        admin.setUsername("superadmin");
        admin.setRoleType(RoleType.SUPER_ADMIN);
        sessionManager.startSession(new UserSession(admin, null, LocalDateTime.now()));

        assertThrows(AuthorizationException.class, () -> votingService.castSimulatedBallot(10L, 77L));
    }

    @Test
    void unapprovedOrIneligibleOrDuplicateOrOutOfWindowVoteRejected() {
        // 1. Unapproved voter
        Voter pending = loginVoter(101L, 5L, VerificationStatus.PENDING, AccountStatus.ACTIVE);
        when(voterRepository.findByUserId(50L)).thenReturn(Optional.of(pending));
        assertThrows(VotingException.class, () -> votingService.castSimulatedBallot(10L, 77L));

        // 2. Voting before opening window
        Voter approved = loginVoter(102L, 5L, VerificationStatus.APPROVED, AccountStatus.ACTIVE);
        when(voterRepository.findByUserId(50L)).thenReturn(Optional.of(approved));
        Election futureOpen = electionWithWindow(10L, ElectionStatus.OPEN,
                LocalDateTime.of(2026, 6, 15, 8, 0), LocalDateTime.of(2026, 6, 20, 18, 0));
        when(electionRepository.findById(10L)).thenReturn(Optional.of(futureOpen));
        assertThrows(VotingException.class, () -> votingService.castSimulatedBallot(10L, 77L));

        // 3. Voting after closing window
        Election expiredOpen = electionWithWindow(10L, ElectionStatus.OPEN,
                LocalDateTime.of(2026, 6, 1, 8, 0), LocalDateTime.of(2026, 6, 5, 18, 0));
        when(electionRepository.findById(10L)).thenReturn(Optional.of(expiredOpen));
        assertThrows(VotingException.class, () -> votingService.castSimulatedBallot(10L, 77L));

        // 4. Duplicate voting rejection
        Election activeOpen = electionWithWindow(10L, ElectionStatus.OPEN,
                LocalDateTime.of(2026, 6, 1, 8, 0), LocalDateTime.of(2026, 6, 20, 18, 0));
        when(electionRepository.findById(10L)).thenReturn(Optional.of(activeOpen));
        when(votingRepository.isVoterEligible(10L, 102L, 5L)).thenReturn(true);
        when(votingRepository.hasParticipated(10L, 102L)).thenReturn(true);
        assertThrows(VotingException.class, () -> votingService.castSimulatedBallot(10L, 77L));

        // 5. Invalid candidate for constituency
        when(votingRepository.hasParticipated(10L, 102L)).thenReturn(false);
        when(electionRepository.isCandidateAssigned(10L, 888L, 5L)).thenReturn(false);
        assertThrows(VotingException.class, () -> votingService.castSimulatedBallot(10L, 888L));
    }

    private Voter loginVoter(long voterId, long constId, VerificationStatus vs, AccountStatus as) {
        User u = new User();
        u.setUserId(50L);
        u.setUsername("voter.demo");
        u.setRoleType(RoleType.VOTER);
        u.setAccountStatus(as);
        Voter v = new Voter();
        v.setVoterId(voterId);
        v.setUserId(50L);
        v.setConstituencyId(constId);
        v.setVerificationStatus(vs);
        v.setAccountStatus(as);
        sessionManager.startSession(new UserSession(u, v, LocalDateTime.now()));
        return v;
    }

    private Election electionWithWindow(long id, ElectionStatus status, LocalDateTime start, LocalDateTime end) {
        Election e = new Election();
        e.setElectionId(id);
        e.setElectionCode("ELC-SIM-" + id);
        e.setElectionName("Simulated Election");
        e.setStatus(status);
        e.setStartDateTime(start);
        e.setEndDateTime(end);
        return e;
    }
}
