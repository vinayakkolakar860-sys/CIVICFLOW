package com.civicflow.service;

import com.civicflow.audit.AuditService;
import com.civicflow.exception.DuplicateRecordException;
import com.civicflow.exception.ResourceNotFoundException;
import com.civicflow.exception.ValidationException;
import com.civicflow.model.AuditResultStatus;
import com.civicflow.model.Constituency;
import com.civicflow.model.ConstituencyStatus;
import com.civicflow.model.Election;
import com.civicflow.repository.ConstituencyRepository;
import com.civicflow.repository.ElectionRepository;
import com.civicflow.security.AccessControlService;
import com.civicflow.validation.ValidationUtils;

import java.util.List;

/**
 * Manages fictional electoral constituencies and referential protection rules.
 */
public class ConstituencyService {

    private final ConstituencyRepository constituencyRepository;
    private final ElectionRepository electionRepository;
    private final AccessControlService accessControl;
    private final AuditService auditService;

    public ConstituencyService(ConstituencyRepository constituencyRepository,
                               ElectionRepository electionRepository,
                               AccessControlService accessControl,
                               AuditService auditService) {
        this.constituencyRepository = constituencyRepository;
        this.electionRepository = electionRepository;
        this.accessControl = accessControl;
        this.auditService = auditService;
    }

    public List<Constituency> getAll(String search, ConstituencyStatus statusFilter) {
        accessControl.requireAuthenticated();
        return constituencyRepository.findAll(search, statusFilter);
    }

    public Constituency getById(long constituencyId) {
        accessControl.requireAuthenticated();
        return constituencyRepository.findById(constituencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Constituency not found."));
    }

    public Constituency create(Constituency constituency) {
        accessControl.requireAdmin();
        validate(constituency);
        if (constituencyRepository.findByCode(constituency.getConstituencyCode()).isPresent()) {
            throw new DuplicateRecordException("Constituency code '" + constituency.getConstituencyCode() + "' already exists.");
        }
        if (constituency.getStatus() == null) {
            constituency.setStatus(ConstituencyStatus.ACTIVE);
        }
        constituencyRepository.insert(constituency);
        auditService.record("CONSTITUENCY", "CREATE_CONSTITUENCY",
                "Created constituency " + constituency.getConstituencyCode() + " (" + constituency.getConstituencyName() + ")",
                AuditResultStatus.SUCCESS);
        return constituency;
    }

    public void update(Constituency constituency) {
        accessControl.requireAdmin();
        validate(constituency);
        constituencyRepository.findById(constituency.getConstituencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Constituency not found."));
        constituencyRepository.findByCode(constituency.getConstituencyCode())
                .filter(existing -> existing.getConstituencyId() != constituency.getConstituencyId())
                .ifPresent(existing -> {
                    throw new DuplicateRecordException("Constituency code '" + constituency.getConstituencyCode() + "' is already used.");
                });

        constituencyRepository.update(constituency);
        auditService.record("CONSTITUENCY", "UPDATE_CONSTITUENCY",
                "Updated constituency " + constituency.getConstituencyCode(), AuditResultStatus.SUCCESS);
    }

    public void delete(long constituencyId) {
        accessControl.requireAdmin();
        Constituency existing = constituencyRepository.findById(constituencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Constituency not found."));
        if (constituencyRepository.hasRelatedRecords(constituencyId)) {
            throw new ValidationException(
                    "Cannot delete constituency '" + existing.getConstituencyName()
                            + "' because voters, candidates, or elections are associated with it.");
        }
        constituencyRepository.delete(constituencyId);
        auditService.record("CONSTITUENCY", "DELETE_CONSTITUENCY",
                "Deleted constituency " + existing.getConstituencyCode(), AuditResultStatus.SUCCESS);
    }

    public List<Election> getElectionHistory(long constituencyId) {
        accessControl.requireAdmin();
        return electionRepository.findByConstituency(constituencyId);
    }

    private void validate(Constituency c) {
        if (c == null) throw new ValidationException("Constituency data is required.");
        c.setConstituencyCode(ValidationUtils.validateCode(c.getConstituencyCode(), "Constituency code"));
        c.setConstituencyName(ValidationUtils.requireMaxLength(ValidationUtils.requireNonBlank(c.getConstituencyName(), "Constituency name"), 120, "Constituency name"));
        c.setCity(ValidationUtils.requireMaxLength(ValidationUtils.requireNonBlank(c.getCity(), "City"), 80, "City"));
        c.setState(ValidationUtils.requireMaxLength(ValidationUtils.requireNonBlank(c.getState(), "State"), 80, "State"));
        c.setDescription(ValidationUtils.requireMaxLength(c.getDescription(), 1000, "Description"));
    }
}
