package com.civicflow.service;

import com.civicflow.audit.AuditService;
import com.civicflow.exception.DuplicateRecordException;
import com.civicflow.exception.ResourceNotFoundException;
import com.civicflow.exception.ValidationException;
import com.civicflow.model.AccountStatus;
import com.civicflow.model.AuditResultStatus;
import com.civicflow.model.RoleType;
import com.civicflow.model.User;
import com.civicflow.repository.UserRepository;
import com.civicflow.security.AccessControlService;
import com.civicflow.security.PasswordService;
import com.civicflow.security.UserSession;
import com.civicflow.validation.ValidationUtils;

import java.util.List;

/**
 * Manages administrative user accounts (SUPER_ADMIN only).
 */
public class UserManagementService {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final AccessControlService accessControl;
    private final AuditService auditService;

    public UserManagementService(UserRepository userRepository,
                                 PasswordService passwordService,
                                 AccessControlService accessControl,
                                 AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.accessControl = accessControl;
        this.auditService = auditService;
    }

    public List<User> getAllAdminAccounts() {
        accessControl.requireSuperAdmin();
        return userRepository.findAllAdmins();
    }

    public User createAdminAccount(String username, String fullName, String email, RoleType roleType, String initialPassword) {
        accessControl.requireSuperAdmin();
        String validUsername = ValidationUtils.validateUsername(username);
        String validName = ValidationUtils.requireMaxLength(ValidationUtils.requireNonBlank(fullName, "Full name"), 120, "Full name");
        String validEmail = ValidationUtils.validateEmail(email, "Email");
        if (roleType == null || !roleType.isAdmin()) {
            throw new ValidationException("Administrative accounts must have SUPER_ADMIN or ELECTION_ADMIN role.");
        }
        passwordService.validatePasswordStrength(initialPassword);

        if (userRepository.findByUsername(validUsername).isPresent()) {
            throw new DuplicateRecordException("Username '" + validUsername + "' is already taken.");
        }
        if (userRepository.findByEmail(validEmail).isPresent()) {
            throw new DuplicateRecordException("Email '" + validEmail + "' is already registered.");
        }

        User user = new User();
        user.setUsername(validUsername);
        user.setFullName(validName);
        user.setEmail(validEmail);
        user.setRoleType(roleType);
        user.setRoleId(userRepository.getRoleId(roleType));
        user.setPasswordHash(passwordService.hashPassword(initialPassword));
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setMustChangePassword(true);

        userRepository.insert(user);
        auditService.record("USER_ADMIN", "CREATE_ADMIN_ACCOUNT",
                "Created " + roleType.name() + " account: " + validUsername, AuditResultStatus.SUCCESS);
        return user;
    }

    public void updateAdminRole(long targetUserId, RoleType newRole) {
        UserSession current = accessControl.requireSuperAdmin();
        if (newRole == null || !newRole.isAdmin()) {
            throw new ValidationException("Admin role must be SUPER_ADMIN or ELECTION_ADMIN.");
        }
        if (current.getUserId() == targetUserId) {
            throw new ValidationException("You cannot demote or change your own active role.");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found."));
        if (!target.getRoleType().isAdmin()) {
            throw new ValidationException("Cannot assign admin role directly to a voter account.");
        }

        userRepository.updateRole(targetUserId, newRole);
        auditService.record("USER_ADMIN", "ROLE_CHANGED",
                "Changed role for " + target.getUsername() + " from " + target.getRoleType() + " to " + newRole,
                AuditResultStatus.SUCCESS);
    }

    public void updateAccountStatus(long targetUserId, AccountStatus newStatus) {
        UserSession current = accessControl.requireSuperAdmin();
        if (current.getUserId() == targetUserId && newStatus != AccountStatus.ACTIVE) {
            throw new ValidationException("You cannot suspend or deactivate your own active account.");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found."));
        userRepository.updateAccountStatus(targetUserId, newStatus);
        auditService.record("USER_ADMIN", "ADMIN_STATUS_CHANGED",
                "Set account status of " + target.getUsername() + " to " + newStatus, AuditResultStatus.SUCCESS);
    }

    public void updateOwnProfile(String fullName, String email) {
        UserSession session = accessControl.requireAuthenticated();
        String validName = ValidationUtils.requireMaxLength(ValidationUtils.requireNonBlank(fullName, "Full name"), 120, "Full name");
        String validEmail = ValidationUtils.validateEmail(email, "Email");
        userRepository.updateProfile(session.getUserId(), validName, validEmail);
        session.getUser().setFullName(validName);
        session.getUser().setEmail(validEmail);
        auditService.record("PROFILE", "UPDATE_PROFILE", "Updated own user profile", AuditResultStatus.SUCCESS);
    }
}
