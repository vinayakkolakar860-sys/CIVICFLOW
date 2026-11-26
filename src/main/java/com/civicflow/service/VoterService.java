package com.civicflow.service;

import com.civicflow.audit.AuditService;
import com.civicflow.config.DatabaseManager;
import com.civicflow.exception.DuplicateRecordException;
import com.civicflow.exception.ResourceNotFoundException;
import com.civicflow.exception.ValidationException;
import com.civicflow.model.*;
import com.civicflow.repository.ConstituencyRepository;
import com.civicflow.repository.UserRepository;
import com.civicflow.repository.VoterRepository;
import com.civicflow.repository.VotingRepository;
import com.civicflow.security.AccessControlService;
import com.civicflow.security.PasswordService;
import com.civicflow.validation.ValidationUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Business service for fictional voter registration, verification, status lifecycle, and participation lookup.
 */
public class VoterService {

    private final DatabaseManager db;
    private final VoterRepository voterRepository;
    private final UserRepository userRepository;
    private final ConstituencyRepository constituencyRepository;
    private final VotingRepository votingRepository;
    private final PasswordService passwordService;
    private final AccessControlService accessControl;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final Clock clock;

    public VoterService(DatabaseManager db,
                        VoterRepository voterRepository,
                        UserRepository userRepository,
                        ConstituencyRepository constituencyRepository,
                        VotingRepository votingRepository,
                        PasswordService passwordService,
                        AccessControlService accessControl,
                        SettingsService settingsService,
                        AuditService auditService,
                        Clock clock) {
        this.db = db;
        this.voterRepository = voterRepository;
        this.userRepository = userRepository;
        this.constituencyRepository = constituencyRepository;
        this.votingRepository = votingRepository;
        this.passwordService = passwordService;
        this.accessControl = accessControl;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.clock = clock;
    }

    public PageResult<Voter> searchVoters(String query, VerificationStatus verificationStatus,
                                          AccountStatus accountStatus, Long constituencyId,
                                          int pageNumber, int pageSize) {
        accessControl.requireAdmin();
        return voterRepository.search(query, verificationStatus, accountStatus, constituencyId, pageNumber, pageSize);
    }

    public List<Voter> findAllVoters(String query, VerificationStatus verificationStatus,
                                     AccountStatus accountStatus, Long constituencyId) {
        accessControl.requireAdmin();
        return voterRepository.findAll(query, verificationStatus, accountStatus, constituencyId);
    }

    public Voter getById(long voterId) {
        accessControl.requireAdmin();
        return voterRepository.findById(voterId)
                .orElseThrow(() -> new ResourceNotFoundException("Voter record not found."));
    }

    public Voter getOwnVoterProfile() {
        var session = accessControl.requireVoter();
        return voterRepository.findByUserId(session.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Voter profile not found."));
    }

    /**
     * Registers a new fictional voter and creates their linked VOTER user account atomically.
     */
    public Voter registerVoter(Voter voter, String username, String initialPassword) {
        accessControl.requireAdmin();
        validateVoterFields(voter);
        String validUsername = ValidationUtils.validateUsername(username);
        passwordService.validatePasswordStrength(initialPassword);

        if (constituencyRepository.findById(voter.getConstituencyId()).isEmpty()) {
            throw new ValidationException("Selected constituency does not exist.");
        }
        if (voterRepository.findByEmail(voter.getEmail()).isPresent()
                || userRepository.findByEmail(voter.getEmail()).isPresent()) {
            throw new DuplicateRecordException("A voter or user with email '" + voter.getEmail() + "' already exists.");
        }
        if (userRepository.findByUsername(validUsername).isPresent()) {
            throw new DuplicateRecordException("Username '" + validUsername + "' is already taken.");
        }

        String voterCode = generateUniqueVoterCode();
        voter.setVoterCode(voterCode);
        voter.setRegistrationDate(LocalDate.now(clock));
        voter.setAccountStatus(AccountStatus.ACTIVE);
        voter.setVerificationStatus(VerificationStatus.PENDING);

        int voterRoleId = userRepository.getRoleId(RoleType.VOTER);
        String hash = passwordService.hashPassword(initialPassword);

        User user = new User();
        user.setUsername(validUsername);
        user.setPasswordHash(hash);
        user.setRoleId(voterRoleId);
        user.setRoleType(RoleType.VOTER);
        user.setEmail(voter.getEmail());
        user.setFullName(voter.getFullName());
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(true);

        db.executeInTransaction(conn -> {
            long userId = userRepository.insert(conn, user);
            voter.setUserId(userId);
            voterRepository.insert(conn, voter);
            return null;
        });

        auditService.record("VOTER", "REGISTER_VOTER",
                "Registered simulated voter " + voter.getVoterCode() + " (" + voter.getFullName() + ")",
                AuditResultStatus.SUCCESS);
        return voterRepository.findById(voter.getVoterId()).orElse(voter);
    }

    public void updateVoter(Voter voter) {
        accessControl.requireAdmin();
        validateVoterFields(voter);
        Voter existing = voterRepository.findById(voter.getVoterId())
                .orElseThrow(() -> new ResourceNotFoundException("Voter record not found."));
        if (constituencyRepository.findById(voter.getConstituencyId()).isEmpty()) {
            throw new ValidationException("Selected constituency does not exist.");
        }
        voterRepository.findByEmail(voter.getEmail())
                .filter(v -> v.getVoterId() != voter.getVoterId())
                .ifPresent(v -> {
                    throw new DuplicateRecordException("Email '" + voter.getEmail() + "' belongs to another voter.");
                });

        voterRepository.update(voter);
        userRepository.updateProfile(existing.getUserId(), voter.getFullName(), voter.getEmail());
        auditService.record("VOTER", "UPDATE_VOTER",
                "Updated voter details for " + existing.getVoterCode(), AuditResultStatus.SUCCESS);
    }

    public void approveVoter(long voterId) {
        accessControl.requireAdmin();
        Voter existing = voterRepository.findById(voterId)
                .orElseThrow(() -> new ResourceNotFoundException("Voter record not found."));
        voterRepository.updateVerificationStatus(voterId, VerificationStatus.APPROVED);
        auditService.record("VOTER", "APPROVE_VOTER",
                "Approved voter registration " + existing.getVoterCode(), AuditResultStatus.SUCCESS);
    }

    public void rejectVoter(long voterId) {
        accessControl.requireAdmin();
        Voter existing = voterRepository.findById(voterId)
                .orElseThrow(() -> new ResourceNotFoundException("Voter record not found."));
        voterRepository.updateVerificationStatus(voterId, VerificationStatus.REJECTED);
        auditService.record("VOTER", "REJECT_VOTER",
                "Rejected voter registration " + existing.getVoterCode(), AuditResultStatus.SUCCESS);
    }

    public void suspendVoter(long voterId) {
        accessControl.requireAdmin();
        Voter existing = voterRepository.findById(voterId)
                .orElseThrow(() -> new ResourceNotFoundException("Voter record not found."));
        db.executeInTransaction(conn -> {
            voterRepository.updateAccountStatus(conn, voterId, AccountStatus.SUSPENDED);
            userRepository.updateAccountStatus(conn, existing.getUserId(), AccountStatus.SUSPENDED);
            return null;
        });
        auditService.record("VOTER", "SUSPEND_VOTER",
                "Suspended voter account " + existing.getVoterCode(), AuditResultStatus.SUCCESS);
    }

    public void reactivateVoter(long voterId) {
        accessControl.requireAdmin();
        Voter existing = voterRepository.findById(voterId)
                .orElseThrow(() -> new ResourceNotFoundException("Voter record not found."));
        db.executeInTransaction(conn -> {
            voterRepository.updateAccountStatus(conn, voterId, AccountStatus.ACTIVE);
            userRepository.updateAccountStatus(conn, existing.getUserId(), AccountStatus.ACTIVE);
            return null;
        });
        auditService.record("VOTER", "REACTIVATE_VOTER",
                "Reactivated voter account " + existing.getVoterCode(), AuditResultStatus.SUCCESS);
    }

    /**
     * Returns participation timestamps/elections ONLY — never reveals ballot selection.
     */
    public List<VoterParticipation> getParticipationHistory(long voterId) {
        var session = accessControl.requireAuthenticated();
        if (session.getRole() == RoleType.VOTER) {
            if (session.getVoterProfile() == null || session.getVoterProfile().getVoterId() != voterId) {
                throw new com.civicflow.exception.AuthorizationException("Voters may only view their own participation status.");
            }
        }
        return votingRepository.findParticipationByVoter(voterId);
    }

    private void validateVoterFields(Voter voter) {
        if (voter == null) throw new ValidationException("Voter details are required.");
        voter.setFirstName(ValidationUtils.requireMaxLength(ValidationUtils.requireNonBlank(voter.getFirstName(), "First name"), 60, "First name"));
        voter.setLastName(ValidationUtils.requireMaxLength(ValidationUtils.requireNonBlank(voter.getLastName(), "Last name"), 60, "Last name"));
        voter.setEmail(ValidationUtils.validateEmail(voter.getEmail(), "Email"));
        voter.setPhoneNumber(ValidationUtils.validatePhone(voter.getPhoneNumber(), "Phone number"));
        int minAge = settingsService.getIntSetting(SettingsService.KEY_MIN_VOTER_AGE, 18);
        ValidationUtils.validateVoterDob(voter.getDateOfBirth(), LocalDate.now(clock), minAge);
        voter.setAddress(ValidationUtils.requireMaxLength(ValidationUtils.requireNonBlank(voter.getAddress(), "Address"), 255, "Address"));
        voter.setCity(ValidationUtils.requireMaxLength(ValidationUtils.requireNonBlank(voter.getCity(), "City"), 80, "City"));
        voter.setState(ValidationUtils.requireMaxLength(ValidationUtils.requireNonBlank(voter.getState(), "State"), 80, "State"));
        if (voter.getConstituencyId() <= 0) {
            throw new ValidationException("Constituency assignment is required.");
        }
    }

    private String generateUniqueVoterCode() {
        int year = LocalDate.now(clock).getYear();
        for (int attempt = 0; attempt < 5; attempt++) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
            String candidate = "VTR-" + year + "-" + suffix;
            if (voterRepository.findByCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "VTR-" + year + "-" + System.nanoTime() % 1000000;
    }
}
