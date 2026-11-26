package com.civicflow.service;

import com.civicflow.audit.AuditService;
import com.civicflow.exception.DuplicateRecordException;
import com.civicflow.exception.ResourceNotFoundException;
import com.civicflow.exception.ValidationException;
import com.civicflow.model.AuditResultStatus;
import com.civicflow.model.Party;
import com.civicflow.model.PartyStatus;
import com.civicflow.repository.PartyRepository;
import com.civicflow.security.AccessControlService;
import com.civicflow.security.FileUploadValidator;
import com.civicflow.validation.ValidationUtils;

import java.nio.file.Path;
import java.util.List;

/**
 * Manages fictional demonstration political parties.
 */
public class PartyService {

    private final PartyRepository partyRepository;
    private final AccessControlService accessControl;
    private final AuditService auditService;
    private final FileUploadValidator uploadValidator;
    private final Path uploadsDir;

    public PartyService(PartyRepository partyRepository,
                        AccessControlService accessControl,
                        AuditService auditService,
                        FileUploadValidator uploadValidator,
                        Path uploadsDir) {
        this.partyRepository = partyRepository;
        this.accessControl = accessControl;
        this.auditService = auditService;
        this.uploadValidator = uploadValidator;
        this.uploadsDir = uploadsDir;
    }

    public List<Party> getAll(String search, PartyStatus statusFilter) {
        accessControl.requireAuthenticated();
        return partyRepository.findAll(search, statusFilter);
    }

    public Party create(Party party, Path optionalLogoFile) {
        accessControl.requireAdmin();
        validate(party);
        if (partyRepository.findByCode(party.getPartyCode()).isPresent()) {
            throw new DuplicateRecordException("Party code '" + party.getPartyCode() + "' already exists.");
        }
        if (partyRepository.findByName(party.getPartyName()).isPresent()) {
            throw new DuplicateRecordException("Party name '" + party.getPartyName() + "' already exists.");
        }
        if (optionalLogoFile != null) {
            String storedPath = uploadValidator.storeValidatedImage(optionalLogoFile, uploadsDir.resolve("parties"), "party_" + party.getPartyCode());
            party.setLogoPath(storedPath);
        }
        if (party.getStatus() == null) {
            party.setStatus(PartyStatus.ACTIVE);
        }
        partyRepository.insert(party);
        auditService.record("PARTY", "CREATE_PARTY",
                "Added fictional party " + party.getPartyCode() + " (" + party.getPartyName() + ")",
                AuditResultStatus.SUCCESS);
        return party;
    }

    public void update(Party party, Path optionalNewLogoFile) {
        accessControl.requireAdmin();
        validate(party);
        Party existing = partyRepository.findById(party.getPartyId())
                .orElseThrow(() -> new ResourceNotFoundException("Party not found."));
        partyRepository.findByCode(party.getPartyCode())
                .filter(p -> p.getPartyId() != party.getPartyId())
                .ifPresent(p -> {
                    throw new DuplicateRecordException("Party code '" + party.getPartyCode() + "' is already used.");
                });
        partyRepository.findByName(party.getPartyName())
                .filter(p -> p.getPartyId() != party.getPartyId())
                .ifPresent(p -> {
                    throw new DuplicateRecordException("Party name '" + party.getPartyName() + "' is already used.");
                });
        if (optionalNewLogoFile != null) {
            String storedPath = uploadValidator.storeValidatedImage(optionalNewLogoFile, uploadsDir.resolve("parties"), "party_" + party.getPartyCode());
            party.setLogoPath(storedPath);
        } else if (party.getLogoPath() == null) {
            party.setLogoPath(existing.getLogoPath());
        }
        partyRepository.update(party);
        auditService.record("PARTY", "UPDATE_PARTY",
                "Updated fictional party " + party.getPartyCode(), AuditResultStatus.SUCCESS);
    }

    public void delete(long partyId) {
        accessControl.requireAdmin();
        Party existing = partyRepository.findById(partyId)
                .orElseThrow(() -> new ResourceNotFoundException("Party not found."));
        if (partyRepository.hasRelatedCandidates(partyId)) {
            throw new ValidationException("Cannot delete party '" + existing.getPartyName() + "' while candidates belong to it.");
        }
        partyRepository.delete(partyId);
        auditService.record("PARTY", "DELETE_PARTY",
                "Deleted fictional party " + existing.getPartyCode(), AuditResultStatus.SUCCESS);
    }

    private void validate(Party p) {
        if (p == null) throw new ValidationException("Party details are required.");
        p.setPartyCode(ValidationUtils.validateCode(p.getPartyCode(), "Party code"));
        p.setPartyName(ValidationUtils.requireMaxLength(ValidationUtils.requireNonBlank(p.getPartyName(), "Party name"), 120, "Party name"));
        p.setDescription(ValidationUtils.requireMaxLength(p.getDescription(), 1000, "Description"));
    }
}
