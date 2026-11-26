package com.civicflow.service;

import com.civicflow.exception.AuthorizationException;
import com.civicflow.model.*;
import com.civicflow.repository.ElectionRepository;
import com.civicflow.repository.PartyRepository;
import com.civicflow.repository.ResultRepository;
import com.civicflow.security.AccessControlService;
import com.civicflow.security.SessionManager;
import com.civicflow.security.UserSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultServiceTest {

    @Mock private ElectionRepository electionRepository;
    @Mock private ResultRepository resultRepository;
    @Mock private PartyRepository partyRepository;

    private SessionManager sessionManager;
    private ResultService resultService;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
        resultService = new ResultService(electionRepository, resultRepository, partyRepository, new AccessControlService(sessionManager));
    }

    @Test
    void votersCannotViewUnpublishedResults() {
        loginAs(RoleType.VOTER);
        Election closed = new Election();
        closed.setElectionId(1L);
        closed.setStatus(ElectionStatus.CLOSED);
        when(electionRepository.findById(1L)).thenReturn(Optional.of(closed));

        assertThrows(AuthorizationException.class, () -> resultService.getElectionResults(1L));
    }

    @Test
    void calculatesPercentagesAndHandlesZeroVotesAccurately() {
        loginAs(RoleType.VOTER);
        Election published = new Election();
        published.setElectionId(2L);
        published.setElectionName("Published Mock Poll");
        published.setStatus(ElectionStatus.PUBLISHED);

        CandidateResult c1 = new CandidateResult();
        c1.setCandidateId(1L);
        c1.setCandidateName("Avery Chen");
        c1.setPartyCode("CFA");
        c1.setConstituencyId(10L);
        c1.setVoteCount(3);

        CandidateResult c2 = new CandidateResult();
        c2.setCandidateId(2L);
        c2.setCandidateName("Marcus Bell");
        c2.setPartyCode("GIP");
        c2.setConstituencyId(10L);
        c2.setVoteCount(1);

        CandidateResult c3 = new CandidateResult();
        c3.setCandidateId(3L);
        c3.setCandidateName("Zero Vote Candidate");
        c3.setPartyCode("UPG");
        c3.setConstituencyId(10L);
        c3.setVoteCount(0);

        ConstituencyResult cr = new ConstituencyResult();
        cr.setConstituencyId(10L);
        cr.setConstituencyName("Northbrook District");
        cr.setEligibleVoters(8);
        cr.setTotalParticipants(4);
        cr.setTotalBallots(4);
        cr.setTurnoutPercentage(50.0);

        when(electionRepository.findById(2L)).thenReturn(Optional.of(published));
        when(resultRepository.countEligibleVoters(2L)).thenReturn(8L);
        when(resultRepository.countTotalParticipants(2L)).thenReturn(4L);
        when(resultRepository.countTotalBallots(2L)).thenReturn(4L);
        when(resultRepository.aggregateCandidateResults(2L)).thenReturn(List.of(c1, c2, c3));
        when(resultRepository.aggregateConstituencyStats(2L)).thenReturn(List.of(cr));

        ElectionResultSummary summary = resultService.getElectionResults(2L);
        assertTrue(summary.isPublished());
        assertEquals(4L, summary.getTotalBallotsCast());
        assertEquals(50.0, summary.getTurnoutPercentage());
        assertEquals(75.0, c1.getPercentage());
        assertEquals(25.0, c2.getPercentage());
        assertEquals(0.0, c3.getPercentage());
        assertTrue(cr.getLeadingCandidateName().contains("Avery Chen"));
    }

    @Test
    void handlesZeroTotalBallotsWithoutNaN() {
        loginAs(RoleType.SUPER_ADMIN);
        Election closed = new Election();
        closed.setElectionId(3L);
        closed.setStatus(ElectionStatus.CLOSED);

        CandidateResult c1 = new CandidateResult();
        c1.setCandidateId(1L);
        c1.setCandidateName("No Ballots Candidate");
        c1.setConstituencyId(10L);
        c1.setVoteCount(0);

        ConstituencyResult cr = new ConstituencyResult();
        cr.setConstituencyId(10L);
        cr.setEligibleVoters(0);
        cr.setTotalParticipants(0);
        cr.setTotalBallots(0);

        when(electionRepository.findById(3L)).thenReturn(Optional.of(closed));
        when(resultRepository.countEligibleVoters(3L)).thenReturn(0L);
        when(resultRepository.countTotalParticipants(3L)).thenReturn(0L);
        when(resultRepository.countTotalBallots(3L)).thenReturn(0L);
        when(resultRepository.aggregateCandidateResults(3L)).thenReturn(List.of(c1));
        when(resultRepository.aggregateConstituencyStats(3L)).thenReturn(List.of(cr));

        ElectionResultSummary summary = resultService.getElectionResults(3L);
        assertEquals(0.0, summary.getTurnoutPercentage());
        assertEquals(0.0, c1.getPercentage());
        assertEquals("No ballots cast", cr.getLeadingCandidateName());
    }

    @Test
    void calculatesPartyResultsWithCorrectPercentagesAndSortOrder() {
        CandidateResult c1 = new CandidateResult();
        c1.setPartyCode("BJP");
        c1.setPartyName("BJP");
        c1.setVoteCount(10);

        CandidateResult c2 = new CandidateResult();
        c2.setPartyCode("BJP");
        c2.setPartyName("BJP");
        c2.setVoteCount(5); // Total BJP = 15

        CandidateResult c3 = new CandidateResult();
        c3.setPartyCode("INC");
        c3.setPartyName("INC");
        c3.setVoteCount(8); // Total INC = 8

        CandidateResult c4 = new CandidateResult();
        c4.setPartyCode("AAP");
        c4.setPartyName("AAP");
        c4.setVoteCount(2); // Total AAP = 2

        List<PartyResult> parties = resultService.calculatePartyResults(List.of(c1, c2, c3, c4), 25);
        assertEquals(3, parties.size());

        // 1st: BJP (15 votes, 60.0%)
        assertEquals("BJP", parties.get(0).getPartyCode());
        assertEquals(15, parties.get(0).getTotalVotes());
        assertEquals(60.0, parties.get(0).getVotePercentage());
        assertEquals(2, parties.get(0).getCandidateCount());
        assertTrue(parties.get(0).isLeading());
        assertFalse(parties.get(0).isTiedForLead());

        // 2nd: INC (8 votes, 32.0%)
        assertEquals("INC", parties.get(1).getPartyCode());
        assertEquals(8, parties.get(1).getTotalVotes());
        assertEquals(32.0, parties.get(1).getVotePercentage());
        assertEquals(1, parties.get(1).getCandidateCount());
        assertFalse(parties.get(1).isLeading());

        // 3rd: AAP (2 votes, 8.0%)
        assertEquals("AAP", parties.get(2).getPartyCode());
        assertEquals(2, parties.get(2).getTotalVotes());
        assertEquals(8.0, parties.get(2).getVotePercentage());
        assertEquals(1, parties.get(2).getCandidateCount());
        assertFalse(parties.get(2).isLeading());
    }

    @Test
    void handlesPartyTiesAccurately() {
        CandidateResult c1 = new CandidateResult();
        c1.setPartyCode("BJP");
        c1.setPartyName("BJP");
        c1.setVoteCount(10);

        CandidateResult c2 = new CandidateResult();
        c2.setPartyCode("INC");
        c2.setPartyName("INC");
        c2.setVoteCount(10);

        List<PartyResult> parties = resultService.calculatePartyResults(List.of(c1, c2), 20);
        assertEquals(2, parties.size());
        assertTrue(parties.get(0).isLeading());
        assertTrue(parties.get(0).isTiedForLead());
        assertTrue(parties.get(1).isLeading());
        assertTrue(parties.get(1).isTiedForLead());
    }

    @Test
    void handlesZeroCandidateElectionGracefullyAndPopulatesActiveParties() {
        loginAs(RoleType.VOTER);
        Election published = new Election();
        published.setElectionId(99L);
        published.setElectionName("Zero Candidate Election");
        published.setStatus(ElectionStatus.PUBLISHED);

        Party p1 = new Party();
        p1.setPartyId(1L);
        p1.setPartyCode("BJP");
        p1.setPartyName("BJP");
        p1.setStatus(PartyStatus.ACTIVE);

        Party p2 = new Party();
        p2.setPartyId(2L);
        p2.setPartyCode("INC");
        p2.setPartyName("INC");
        p2.setStatus(PartyStatus.ACTIVE);

        when(electionRepository.findById(99L)).thenReturn(Optional.of(published));
        when(resultRepository.countEligibleVoters(99L)).thenReturn(0L);
        when(resultRepository.countTotalParticipants(99L)).thenReturn(0L);
        when(resultRepository.countTotalBallots(99L)).thenReturn(0L);
        when(resultRepository.aggregateCandidateResults(99L)).thenReturn(Collections.emptyList());
        when(resultRepository.aggregateConstituencyStats(99L)).thenReturn(Collections.emptyList());
        when(partyRepository.findAll(null, PartyStatus.ACTIVE)).thenReturn(List.of(p1, p2));

        ElectionResultSummary summary = resultService.getElectionResults(99L);
        assertNotNull(summary);
        assertTrue(summary.getCandidateResults().isEmpty());
        assertEquals(0L, summary.getTotalBallotsCast());
        assertEquals(0.0, summary.getTurnoutPercentage());
        assertEquals("No ballots cast yet", summary.getLeadingPartySummary());
        assertEquals(2, summary.getPartyResults().size());
        assertEquals(0, summary.getPartyResults().get(0).getTotalVotes());
        assertEquals(0.0, summary.getPartyResults().get(0).getVotePercentage());
        assertFalse(summary.getPartyResults().get(0).isLeading());

        // Verify that a database error from partyRepository is caught and logged gracefully without throwing exception
        when(partyRepository.findAll(null, PartyStatus.ACTIVE)).thenThrow(new com.civicflow.exception.DatabaseException("Simulated party DB failure"));
        List<PartyResult> fallback = resultService.calculatePartyResults(Collections.emptyList(), 0);
        assertNotNull(fallback);
        assertTrue(fallback.isEmpty());
    }

    private void loginAs(RoleType role) {
        User u = new User();
        u.setUserId(1L);
        u.setUsername("actor");
        u.setRoleType(role);
        Voter v = role == RoleType.VOTER ? new Voter() : null;
        sessionManager.startSession(new UserSession(u, v, LocalDateTime.now()));
    }
}
