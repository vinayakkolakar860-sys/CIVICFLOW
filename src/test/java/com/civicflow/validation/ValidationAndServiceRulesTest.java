package com.civicflow.validation;

import com.civicflow.audit.AuditService;
import com.civicflow.config.DatabaseManager;
import com.civicflow.exception.DuplicateRecordException;
import com.civicflow.exception.ValidationException;
import com.civicflow.model.*;
import com.civicflow.repository.*;
import com.civicflow.security.AccessControlService;
import com.civicflow.security.FileUploadValidator;
import com.civicflow.security.PasswordService;
import com.civicflow.security.SessionManager;
import com.civicflow.security.UserSession;
import com.civicflow.service.CandidateService;
import com.civicflow.service.ConstituencyService;
import com.civicflow.service.SettingsService;
import com.civicflow.service.VoterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidationAndServiceRulesTest {

    @Mock private DatabaseManager db;
    @Mock private VoterRepository voterRepository;
    @Mock private UserRepository userRepository;
    @Mock private ConstituencyRepository constituencyRepository;
    @Mock private PartyRepository partyRepository;
    @Mock private CandidateRepository candidateRepository;
    @Mock private ElectionRepository electionRepository;
    @Mock private VotingRepository votingRepository;
    @Mock private SettingsService settingsService;
    @Mock private AuditService auditService;

    private AccessControlService ac;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        SessionManager sm = new SessionManager();
        User admin = new User();
        admin.setUserId(1L);
        admin.setUsername("superadmin");
        admin.setRoleType(RoleType.SUPER_ADMIN);
        sm.startSession(new UserSession(admin, null, LocalDateTime.now()));
        ac = new AccessControlService(sm);
        fixedClock = Clock.fixed(Instant.parse("2026-06-10T10:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void validationUtilsValidatesEmailPhoneAgeAndDates() {
        assertEquals("demo@civicflow.edu", ValidationUtils.validateEmail("  Demo@CivicFlow.EDU ", "Email"));
        assertThrows(ValidationException.class, () -> ValidationUtils.validateEmail("bad-email", "Email"));
        assertThrows(ValidationException.class, () -> ValidationUtils.validatePhone("12", "Phone"));
        assertThrows(ValidationException.class, () -> ValidationUtils.validateVoterDob(
                LocalDate.of(2015, 1, 1), LocalDate.of(2026, 6, 10), 18));
        assertDoesNotThrow(() -> ValidationUtils.validateVoterDob(
                LocalDate.of(2000, 1, 1), LocalDate.of(2026, 6, 10), 18));
        assertThrows(ValidationException.class, () -> ValidationUtils.validateCandidateAge(16));
        assertThrows(ValidationException.class, () -> ValidationUtils.validateElectionDateRange(
                LocalDateTime.of(2026, 6, 10, 12, 0), LocalDateTime.of(2026, 6, 10, 12, 0)));
    }

    @Test
    void voterRegistrationRejectsDuplicateEmail() {
        VoterService voterService = new VoterService(db, voterRepository, userRepository, constituencyRepository,
                votingRepository, new PasswordService(), ac, settingsService, auditService, fixedClock);

        Voter v = validVoter();
        when(settingsService.getIntSetting(SettingsService.KEY_MIN_VOTER_AGE, 18)).thenReturn(18);
        when(constituencyRepository.findById(1L)).thenReturn(Optional.of(new Constituency()));
        when(voterRepository.findByEmail("maya@civicflow.demo")).thenReturn(Optional.of(new Voter()));

        assertThrows(DuplicateRecordException.class, () -> voterService.registerVoter(v, "voter.new", "Abc123"));
    }

    @Test
    void candidateServiceValidatesConstituencyAndProtectsActiveElectionDeletion() {
        CandidateService candidateService = new CandidateService(candidateRepository, constituencyRepository,
                partyRepository, ac, auditService, new FileUploadValidator(), Path.of("uploads"));

        Candidate cand = new Candidate();
        cand.setFullName("Avery Chen");
        cand.setAge(36);
        cand.setEmail("avery@civicflow.demo");
        cand.setContactNumber("555-010-2030");
        cand.setConstituencyId(999L);
        cand.setPartyId(1L);

        when(constituencyRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ValidationException.class, () -> candidateService.registerCandidate(cand, null));

        Candidate existing = new Candidate();
        existing.setCandidateId(7L);
        existing.setFullName("Protected Candidate");
        when(candidateRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(candidateRepository.isAssignedToProtectedElection(7L)).thenReturn(true);
        assertThrows(ValidationException.class, () -> candidateService.deleteCandidate(7L));
    }

    @Test
    void constituencyDeletionBlockedWhenRelatedRecordsExist() {
        ConstituencyService cs = new ConstituencyService(constituencyRepository, electionRepository, ac, auditService);
        Constituency c = new Constituency();
        c.setConstituencyId(1L);
        c.setConstituencyName("Northbrook");
        when(constituencyRepository.findById(1L)).thenReturn(Optional.of(c));
        when(constituencyRepository.hasRelatedRecords(1L)).thenReturn(true);

        assertThrows(ValidationException.class, () -> cs.delete(1L));
    }

    private Voter validVoter() {
        Voter v = new Voter();
        v.setFirstName("Maya");
        v.setLastName("Linwood");
        v.setEmail("maya@civicflow.demo");
        v.setPhoneNumber("+1-555-019-2834");
        v.setDateOfBirth(LocalDate.of(1995, 5, 10));
        v.setAddress("100 Elm Ave");
        v.setCity("Oakridge");
        v.setState("Meridian");
        v.setConstituencyId(1L);
        return v;
    }
}
