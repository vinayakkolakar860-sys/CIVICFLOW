package com.civicflow.service;

import com.civicflow.audit.AuditService;
import com.civicflow.exception.DuplicateRecordException;
import com.civicflow.exception.ResourceNotFoundException;
import com.civicflow.exception.ValidationException;
import com.civicflow.model.AuditResultStatus;
import com.civicflow.model.Candidate;
import com.civicflow.model.CandidateStatus;
import com.civicflow.repository.CandidateRepository;
import com.civicflow.repository.ConstituencyRepository;
import com.civicflow.repository.PartyRepository;
import com.civicflow.security.AccessControlService;
import com.civicflow.security.FileUploadValidator;
import com.civicflow.validation.ValidationUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Business service for managing fictional candidates, constituency/party validation,
 * approval workflows, and protected deletion rules.
 */
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final ConstituencyRepository constituencyRepository;
    private final PartyRepository partyRepository;
    private final AccessControlService accessControl;
    private final AuditService auditService;
    private final FileUploadValidator uploadValidator;
    private final Path uploadsDir;

    public CandidateService(CandidateRepository candidateRepository,
                            ConstituencyRepository constituencyRepository,
                            PartyRepository partyRepository,
                            AccessControlService accessControl,
                            AuditService auditService,
                            FileUploadValidator uploadValidator,
                            Path uploadsDir) {
        this.candidateRepository = candidateRepository;
        this.constituencyRepository = constituencyRepository;
        this.partyRepository = partyRepository;
        this.accessControl = accessControl;
        this.auditService = auditService;
        this.uploadValidator = uploadValidator;
        this.uploadsDir = uploadsDir;
    }

    public List<Candidate> search(String query, Long constituencyId, Long partyId, CandidateStatus status) {
        accessControl.requireAdmin();
        return candidateRepository.search(query, constituencyId, partyId, status);
    }

    public Candidate getById(long candidateId) {
        accessControl.requireAuthenticated();
        return candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found."));
    }

    public Candidate registerCandidate(Candidate candidate, Path optionalPhotoFile) {
        accessControl.requireAdmin();
        validateCandidate(candidate);

        if (constituencyRepository.findById(candidate.getConstituencyId()).isEmpty()) {
            throw new ValidationException("Assigned constituency does not exist.");
        }
        if (partyRepository.findById(candidate.getPartyId()).isEmpty()) {
            throw new ValidationException("Assigned fictional party does not exist.");
        }
        if (candidateRepository.findByEmail(candidate.getEmail()).isPresent()) {
            throw new DuplicateRecordException("A candidate with email '" + candidate.getEmail() + "' already exists.");
        }

        if (candidate.getCandidateCode() == null || candidate.getCandidateCode().isBlank()) {
            candidate.setCandidateCode("CND-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());
        } else {
            candidate.setCandidateCode(ValidationUtils.validateCode(candidate.getCandidateCode(), "Candidate code"));
        }

        if (optionalPhotoFile != null) {
            String stored = uploadValidator.storeValidatedImage(optionalPhotoFile, uploadsDir.resolve("candidates"), "cand_" + candidate.getCandidateCode());
            candidate.setPhotoPath(stored);
        }
        if (candidate.getStatus() == null) {
            candidate.setStatus(CandidateStatus.PENDING);
        }

        candidateRepository.insert(candidate);
        auditService.record("CANDIDATE", "REGISTER_CANDIDATE",
                "Registered candidate " + candidate.getCandidateCode() + " (" + candidate.getFullName() + ")",
                AuditResultStatus.SUCCESS);
        return candidateRepository.findById(candidate.getCandidateId()).orElse(candidate);
    }

    public void updateCandidate(Candidate candidate, Path optionalNewPhotoFile) {
        accessControl.requireAdmin();
        validateCandidate(candidate);
        Candidate existing = candidateRepository.findById(candidate.getCandidateId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found."));

        if (constituencyRepository.findById(candidate.getConstituencyId()).isEmpty()) {
            throw new ValidationException("Assigned constituency does not exist.");
        }
        if (partyRepository.findById(candidate.getPartyId()).isEmpty()) {
            throw new ValidationException("Assigned fictional party does not exist.");
        }
        candidateRepository.findByEmail(candidate.getEmail())
                .filter(c -> c.getCandidateId() != candidate.getCandidateId())
                .ifPresent(c -> {
                    throw new DuplicateRecordException("Email '" + candidate.getEmail() + "' belongs to another candidate.");
                });

        if (optionalNewPhotoFile != null) {
            String stored = uploadValidator.storeValidatedImage(optionalNewPhotoFile, uploadsDir.resolve("candidates"), "cand_" + existing.getCandidateCode());
            candidate.setPhotoPath(stored);
        } else if (candidate.getPhotoPath() == null) {
            candidate.setPhotoPath(existing.getPhotoPath());
        }

        candidateRepository.update(candidate);
        auditService.record("CANDIDATE", "UPDATE_CANDIDATE",
                "Updated candidate " + existing.getCandidateCode() + " (" + candidate.getFullName() + ")",
                AuditResultStatus.SUCCESS);
    }

    public void approveCandidate(long candidateId) {
        accessControl.requireAdmin();
        Candidate existing = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found."));
        candidateRepository.updateStatus(candidateId, CandidateStatus.APPROVED);
        auditService.record("CANDIDATE", "APPROVE_CANDIDATE",
                "Approved candidate " + existing.getCandidateCode(), AuditResultStatus.SUCCESS);
    }

    public void rejectCandidate(long candidateId) {
        accessControl.requireAdmin();
        Candidate existing = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found."));
        candidateRepository.updateStatus(candidateId, CandidateStatus.REJECTED);
        auditService.record("CANDIDATE", "REJECT_CANDIDATE",
                "Rejected candidate " + existing.getCandidateCode(), AuditResultStatus.SUCCESS);
    }

    public void deleteCandidate(long candidateId) {
        accessControl.requireAdmin();
        Candidate existing = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found."));
        if (candidateRepository.isAssignedToProtectedElection(candidateId)) {
            throw new ValidationException(
                    "Cannot delete candidate '" + existing.getFullName()
                            + "' because they are linked to an active, closed, or published election.");
        }
        candidateRepository.delete(candidateId);
        auditService.record("CANDIDATE", "DELETE_CANDIDATE",
                "Deleted candidate " + existing.getCandidateCode(), AuditResultStatus.SUCCESS);
    }

    private void validateCandidate(Candidate c) {
        if (c == null) throw new ValidationException("Candidate details are required.");
        c.setFullName(ValidationUtils.requireMaxLength(ValidationUtils.requireNonBlank(c.getFullName(), "Candidate name"), 120, "Candidate name"));
        ValidationUtils.validateCandidateAge(c.getAge());
        c.setEmail(ValidationUtils.validateEmail(c.getEmail(), "Email"));
        c.setContactNumber(ValidationUtils.validatePhone(c.getContactNumber(), "Contact number"));
        c.setProfileDescription(ValidationUtils.requireMaxLength(c.getProfileDescription(), 2000, "Profile description"));
        if (c.getPartyId() <= 0) throw new ValidationException("Fictional party selection is required.");
        if (c.getConstituencyId() <= 0) throw new ValidationException("Constituency selection is required.");
    }
}
