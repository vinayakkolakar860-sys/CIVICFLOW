package com.civicflow.service;

import com.civicflow.audit.AuditService;
import com.civicflow.exception.DuplicateRecordException;
import com.civicflow.exception.ValidationException;
import com.civicflow.model.*;
import com.civicflow.repository.CandidateRepository;
import com.civicflow.repository.ConstituencyRepository;
import com.civicflow.repository.ElectionRepository;
import com.civicflow.security.AccessControlService;
import com.civicflow.security.SessionManager;
import com.civicflow.security.UserSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElectionServiceTest {

    @Mock private ElectionRepository electionRepository;
    @Mock private ConstituencyRepository constituencyRepository;
    @Mock private CandidateRepository candidateRepository;
    @Mock private AuditService auditService;

    private ElectionService electionService;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        SessionManager sm = new SessionManager();
        User admin = new User();
        admin.setUserId(1L);
        admin.setUsername("superadmin");
        admin.setRoleType(RoleType.SUPER_ADMIN);
        sm.startSession(new UserSession(admin, null, LocalDateTime.now()));
        AccessControlService ac = new AccessControlService(sm);

        fixedClock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC);
        electionService = new ElectionService(electionRepository, constituencyRepository, candidateRepository, ac, auditService, fixedClock);
    }

    @Test
    void createElectionRejectsInvalidDateRange() {
        Election e = new Election();
        e.setElectionName("Invalid Schedule Poll");
        e.setElectionType(ElectionType.GENERAL);
        e.setStartDateTime(LocalDateTime.of(2026, 7, 10, 18, 0));
        e.setEndDateTime(LocalDateTime.of(2026, 7, 10, 9, 0)); // End before start

        assertThrows(ValidationException.class, () -> electionService.createElection(e));
    }

    @Test
    void createElectionStartsInDraftStatus() {
        Election e = new Election();
        e.setElectionName("Campus Mock Election");
        e.setElectionType(ElectionType.CAMPUS);
        e.setStartDateTime(LocalDateTime.of(2026, 7, 1, 9, 0));
        e.setEndDateTime(LocalDateTime.of(2026, 7, 2, 17, 0));

        when(electionRepository.findByCode(any())).thenReturn(Optional.empty());
        when(electionRepository.insert(any())).thenAnswer(inv -> {
            Election arg = inv.getArgument(0);
            arg.setElectionId(42L);
            return 42L;
        });
        when(electionRepository.findById(42L)).thenReturn(Optional.of(e));

        Election created = electionService.createElection(e);
        assertEquals(ElectionStatus.DRAFT, created.getStatus());
        assertEquals(1L, created.getCreatedBy());
    }

    @Test
    void cannotOpenElectionDirectlyFromDraftOrWithoutCandidates() {
        Election draft = sampleElection(10L, ElectionStatus.DRAFT);
        when(electionRepository.findById(10L)).thenReturn(Optional.of(draft));
        assertThrows(ValidationException.class, () -> electionService.openElection(10L));

        Election scheduled = sampleElection(11L, ElectionStatus.SCHEDULED);
        when(electionRepository.findById(11L)).thenReturn(Optional.of(scheduled));
        when(electionRepository.findAssignedConstituencies(11L)).thenReturn(List.of());
        assertThrows(ValidationException.class, () -> electionService.openElection(11L));
    }

    @Test
    void validLifecycleTransitionsSucceedAndPublishedIsImmutable() {
        Election scheduled = sampleElection(12L, ElectionStatus.SCHEDULED);
        Constituency c = new Constituency();
        c.setConstituencyId(1L);
        Candidate cand = new Candidate();
        cand.setCandidateId(5L);

        when(electionRepository.findById(12L)).thenReturn(Optional.of(scheduled));
        when(electionRepository.findAssignedConstituencies(12L)).thenReturn(List.of(c));
        when(electionRepository.findAssignedCandidates(12L, null)).thenReturn(List.of(cand));

        electionService.openElection(12L);
        verify(electionRepository).updateStatus(12L, ElectionStatus.OPEN);

        Election open = sampleElection(12L, ElectionStatus.OPEN);
        when(electionRepository.findById(12L)).thenReturn(Optional.of(open));
        electionService.closeElection(12L);
        verify(electionRepository).updateStatus(12L, ElectionStatus.CLOSED);

        Election closed = sampleElection(12L, ElectionStatus.CLOSED);
        when(electionRepository.findById(12L)).thenReturn(Optional.of(closed));
        electionService.publishResults(12L);
        verify(electionRepository).updateStatus(12L, ElectionStatus.PUBLISHED);

        Election published = sampleElection(12L, ElectionStatus.PUBLISHED);
        when(electionRepository.findById(12L)).thenReturn(Optional.of(published));
        assertThrows(ValidationException.class, () -> electionService.updateElection(published));
        assertThrows(ValidationException.class, () -> electionService.cancelElection(12L));
    }

    @Test
    void duplicateCandidateAssignmentInSameElectionRejected() {
        Election draft = sampleElection(20L, ElectionStatus.DRAFT);
        Constituency c = new Constituency();
        c.setConstituencyId(3L);
        Candidate cand = new Candidate();
        cand.setCandidateId(9L);
        cand.setConstituencyId(3L);
        cand.setStatus(CandidateStatus.APPROVED);

        when(electionRepository.findById(20L)).thenReturn(Optional.of(draft));
        when(candidateRepository.findById(9L)).thenReturn(Optional.of(cand));
        when(electionRepository.findAssignedConstituencies(20L)).thenReturn(List.of(c));
        when(electionRepository.isCandidateAssigned(20L, 9L, 3L)).thenReturn(true);

        assertThrows(DuplicateRecordException.class, () -> electionService.assignCandidate(20L, 9L));
    }

    private Election sampleElection(long id, ElectionStatus status) {
        Election e = new Election();
        e.setElectionId(id);
        e.setElectionCode("ELC-2026-T" + id);
        e.setElectionName("Test Election " + id);
        e.setElectionType(ElectionType.GENERAL);
        e.setStartDateTime(LocalDateTime.of(2026, 6, 1, 8, 0));
        e.setEndDateTime(LocalDateTime.of(2026, 6, 20, 18, 0));
        e.setStatus(status);
        return e;
    }
}
