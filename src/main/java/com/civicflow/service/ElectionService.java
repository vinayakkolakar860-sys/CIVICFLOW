package com.civicflow.service;

import com.civicflow.audit.AuditService;
import com.civicflow.exception.DuplicateRecordException;
import com.civicflow.exception.ResourceNotFoundException;
import com.civicflow.exception.ValidationException;
import com.civicflow.model.*;
import com.civicflow.repository.CandidateRepository;
import com.civicflow.repository.ConstituencyRepository;
import com.civicflow.repository.ElectionRepository;
import com.civicflow.security.AccessControlService;
import com.civicflow.security.UserSession;
import com.civicflow.validation.ElectionStateValidator;
import com.civicflow.validation.ValidationUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Manages simulated election creation, scheduling, constituency/candidate assignment,
 * deterministic clock-based lifecycle transitions, and result publication.
 */
public class ElectionService {

    private final ElectionRepository electionRepository;
    private final ConstituencyRepository constituencyRepository;
    private final CandidateRepository candidateRepository;
    private final AccessControlService accessControl;
    private final AuditService auditService;
    private final Clock clock;

    public ElectionService(ElectionRepository electionRepository,
                           ConstituencyRepository constituencyRepository,
                           CandidateRepository candidateRepository,
                           AccessControlService accessControl,
                           AuditService auditService,
                           Clock clock) {
        this.electionRepository = electionRepository;
        this.constituencyRepository = constituencyRepository;
        this.candidateRepository = candidateRepository;
        this.accessControl = accessControl;
        this.auditService = auditService;
        this.clock = clock;
    }

    public List<Election> getAllElections(String search, ElectionStatus statusFilter) {
        accessControl.requireAdmin();
        return electionRepository.findAll(search, statusFilter);
    }

    public List<Election> getPublishedElections() {
        accessControl.requireAuthenticated();
        return electionRepository.findPublishedElections();
    }

    public List<Election> getEligibleElectionsForCurrentVoter() {
        UserSession session = accessControl.requireVoter();
        return electionRepository.findEligibleForVoter(session.getVoterProfile().getVoterId());
    }

    public Election getById(long electionId) {
        accessControl.requireAuthenticated();
        return electionRepository.findById(electionId)
                .orElseThrow(() -> new ResourceNotFoundException("Election not found."));
    }

    public Election createElection(Election election) {
        UserSession admin = accessControl.requireAdmin();
        validateElection(election);

        if (election.getElectionCode() == null || election.getElectionCode().isBlank()) {
            int year = LocalDateTime.now(clock).getYear();
            election.setElectionCode("ELC-" + year + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase());
        } else {
            election.setElectionCode(ValidationUtils.validateCode(election.getElectionCode(), "Election code"));
        }

        if (electionRepository.findByCode(election.getElectionCode()).isPresent()) {
            throw new DuplicateRecordException("Election code '" + election.getElectionCode() + "' already exists.");
        }

        election.setStatus(ElectionStatus.DRAFT);
        election.setCreatedBy(admin.getUserId());
        electionRepository.insert(election);

        auditService.record("ELECTION", "CREATE_ELECTION",
                "Created election " + election.getElectionCode() + " (" + election.getElectionName() + ")",
                AuditResultStatus.SUCCESS);
        return electionRepository.findById(election.getElectionId()).orElse(election);
    }

    public void updateElection(Election election) {
        accessControl.requireAdmin();
        validateElection(election);
        Election existing = electionRepository.findById(election.getElectionId())
                .orElseThrow(() -> new ResourceNotFoundException("Election not found."));
        ElectionStateValidator.ensureEditable(existing.getStatus());

        electionRepository.update(election);
        auditService.record("ELECTION", "UPDATE_ELECTION",
                "Updated election schedule/details for " + existing.getElectionCode(), AuditResultStatus.SUCCESS);
    }

    public void scheduleElection(long electionId) {
        accessControl.requireAdmin();
        Election election = getRequiredElection(electionId);
        ElectionStateValidator.validateTransition(election.getStatus(), ElectionStatus.SCHEDULED);
        validateReadinessForScheduleOrOpen(election);

        electionRepository.updateStatus(electionId, ElectionStatus.SCHEDULED);
        electionRepository.syncVoterEligibilityForElection(electionId);
        auditService.record("ELECTION", "SCHEDULE_ELECTION",
                "Scheduled election " + election.getElectionCode(), AuditResultStatus.SUCCESS);
    }

    public void openElection(long electionId) {
        accessControl.requireAdmin();
        Election election = getRequiredElection(electionId);
        ElectionStateValidator.validateTransition(election.getStatus(), ElectionStatus.OPEN);
        validateReadinessForScheduleOrOpen(election);

        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isAfter(election.getEndDateTime())) {
            throw new ValidationException("Cannot open an election whose end date/time has already passed.");
        }

        electionRepository.syncVoterEligibilityForElection(electionId);
        electionRepository.updateStatus(electionId, ElectionStatus.OPEN);
        auditService.record("ELECTION", "OPEN_ELECTION",
                "Opened simulated election " + election.getElectionCode() + " for voting", AuditResultStatus.SUCCESS);
    }

    public void closeElection(long electionId) {
        accessControl.requireAdmin();
        Election election = getRequiredElection(electionId);
        ElectionStateValidator.validateTransition(election.getStatus(), ElectionStatus.CLOSED);

        electionRepository.updateStatus(electionId, ElectionStatus.CLOSED);
        auditService.record("ELECTION", "CLOSE_ELECTION",
                "Closed election " + election.getElectionCode() + "; ballot submission disabled", AuditResultStatus.SUCCESS);
    }

    public void publishResults(long electionId) {
        accessControl.requireAdmin();
        Election election = getRequiredElection(electionId);
        ElectionStateValidator.validateTransition(election.getStatus(), ElectionStatus.PUBLISHED);

        electionRepository.updateStatus(electionId, ElectionStatus.PUBLISHED);
        auditService.record("ELECTION", "PUBLISH_RESULTS",
                "Published simulated results for election " + election.getElectionCode(), AuditResultStatus.SUCCESS);
    }

    public void cancelElection(long electionId) {
        accessControl.requireAdmin();
        Election election = getRequiredElection(electionId);
        ElectionStateValidator.validateTransition(election.getStatus(), ElectionStatus.CANCELLED);

        electionRepository.updateStatus(electionId, ElectionStatus.CANCELLED);
        auditService.record("ELECTION", "CANCEL_ELECTION",
                "Cancelled election " + election.getElectionCode(), AuditResultStatus.SUCCESS);
    }

    public void revertToDraft(long electionId) {
        accessControl.requireAdmin();
        Election election = getRequiredElection(electionId);
        ElectionStateValidator.validateTransition(election.getStatus(), ElectionStatus.DRAFT);

        electionRepository.updateStatus(electionId, ElectionStatus.DRAFT);
        auditService.record("ELECTION", "REVERT_TO_DRAFT",
                "Reverted scheduled election " + election.getElectionCode() + " to DRAFT", AuditResultStatus.SUCCESS);
    }

    public void assignConstituency(long electionId, long constituencyId) {
        accessControl.requireAdmin();
        Election election = getRequiredElection(electionId);
        ElectionStateValidator.ensureEditable(election.getStatus());

        Constituency c = constituencyRepository.findById(constituencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Constituency not found."));
        if (c.getStatus() != ConstituencyStatus.ACTIVE) {
            throw new ValidationException("Only ACTIVE constituencies may be assigned to an election.");
        }

        electionRepository.assignConstituency(electionId, constituencyId);
        electionRepository.syncVoterEligibilityForElection(electionId);
        auditService.record("ELECTION", "ASSIGN_CONSTITUENCY",
                "Assigned constituency " + c.getConstituencyCode() + " to " + election.getElectionCode(),
                AuditResultStatus.SUCCESS);
    }

    public void removeConstituency(long electionId, long constituencyId) {
        accessControl.requireAdmin();
        Election election = getRequiredElection(electionId);
        ElectionStateValidator.ensureEditable(election.getStatus());

        electionRepository.removeConstituency(electionId, constituencyId);
        auditService.record("ELECTION", "REMOVE_CONSTITUENCY",
                "Removed constituency ID=" + constituencyId + " from " + election.getElectionCode(),
                AuditResultStatus.SUCCESS);
    }

    public List<Constituency> getAssignedConstituencies(long electionId) {
        accessControl.requireAuthenticated();
        return electionRepository.findAssignedConstituencies(electionId);
    }

    public void assignCandidate(long electionId, long candidateId) {
        accessControl.requireAdmin();
        Election election = getRequiredElection(electionId);
        ElectionStateValidator.ensureEditable(election.getStatus());

        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found."));
        if (candidate.getStatus() != CandidateStatus.APPROVED) {
            throw new ValidationException("Only APPROVED candidates can be assigned to an election.");
        }

        List<Constituency> assignedConstituencies = electionRepository.findAssignedConstituencies(electionId);
        boolean constituencyIncluded = assignedConstituencies.stream()
                .anyMatch(c -> c.getConstituencyId() == candidate.getConstituencyId());
        if (!constituencyIncluded) {
            throw new ValidationException(
                    "Candidate's constituency (" + candidate.getConstituencyName()
                            + ") must be assigned to the election first.");
        }
        if (electionRepository.isCandidateAssigned(electionId, candidateId, candidate.getConstituencyId())) {
            throw new DuplicateRecordException("Candidate is already assigned to this election and constituency.");
        }

        electionRepository.assignCandidate(electionId, candidateId, candidate.getConstituencyId());
        auditService.record("ELECTION", "ASSIGN_CANDIDATE",
                "Assigned candidate " + candidate.getCandidateCode() + " to " + election.getElectionCode(),
                AuditResultStatus.SUCCESS);
    }

    public void removeCandidate(long electionId, long candidateId) {
        accessControl.requireAdmin();
        Election election = getRequiredElection(electionId);
        ElectionStateValidator.ensureEditable(election.getStatus());

        electionRepository.removeCandidate(electionId, candidateId);
        auditService.record("ELECTION", "REMOVE_CANDIDATE",
                "Removed candidate ID=" + candidateId + " from " + election.getElectionCode(),
                AuditResultStatus.SUCCESS);
    }

    public List<Candidate> getAssignedCandidates(long electionId, Long constituencyId) {
        accessControl.requireAuthenticated();
        return electionRepository.findAssignedCandidates(electionId, constituencyId);
    }

    public int syncEligibility(long electionId) {
        accessControl.requireAdmin();
        Election election = getRequiredElection(electionId);
        if (election.getStatus() == ElectionStatus.CLOSED || election.getStatus() == ElectionStatus.PUBLISHED
                || election.getStatus() == ElectionStatus.CANCELLED) {
            throw new ValidationException("Cannot modify voter eligibility for a finalized election.");
        }
        return electionRepository.syncVoterEligibilityForElection(electionId);
    }

    private void validateReadinessForScheduleOrOpen(Election election) {
        List<Constituency> constituencies = electionRepository.findAssignedConstituencies(election.getElectionId());
        if (constituencies.isEmpty()) {
            throw new ValidationException("Election must have at least one assigned constituency before scheduling or opening.");
        }
        List<Candidate> candidates = electionRepository.findAssignedCandidates(election.getElectionId(), null);
        if (candidates.isEmpty()) {
            throw new ValidationException("Election must have at least one approved candidate assigned before scheduling or opening.");
        }
    }

    private Election getRequiredElection(long electionId) {
        return electionRepository.findById(electionId)
                .orElseThrow(() -> new ResourceNotFoundException("Election not found."));
    }

    private void validateElection(Election e) {
        if (e == null) throw new ValidationException("Election details are required.");
        e.setElectionName(ValidationUtils.requireMaxLength(ValidationUtils.requireNonBlank(e.getElectionName(), "Election name"), 150, "Election name"));
        e.setDescription(ValidationUtils.requireMaxLength(e.getDescription(), 2000, "Description"));
        if (e.getElectionType() == null) {
            throw new ValidationException("Election type is required.");
        }
        ValidationUtils.validateElectionDateRange(e.getStartDateTime(), e.getEndDateTime());
    }
}
