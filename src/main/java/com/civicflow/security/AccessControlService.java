package com.civicflow.security;

import com.civicflow.exception.AuthorizationException;
import com.civicflow.model.RoleType;

import java.util.Arrays;

/**
 * Service-layer Role-Based Access Control (RBAC) enforcement.
 * Every protected service method must verify authorization here rather than relying on UI visibility alone.
 */
public class AccessControlService {

    private final SessionManager sessionManager;

    public AccessControlService(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public UserSession requireAuthenticated() {
        return sessionManager.getCurrentSession()
                .orElseThrow(() -> new AuthorizationException("Authentication is required to perform this action."));
    }

    public UserSession requireRole(RoleType... allowedRoles) {
        UserSession session = requireAuthenticated();
        boolean permitted = Arrays.stream(allowedRoles).anyMatch(r -> r == session.getRole());
        if (!permitted) {
            throw new AuthorizationException("Access denied. Your role (" + session.getRole().getDisplayName()
                    + ") is not authorized to perform this operation.");
        }
        return session;
    }

    public UserSession requireSuperAdmin() {
        return requireRole(RoleType.SUPER_ADMIN);
    }

    public UserSession requireAdmin() {
        return requireRole(RoleType.SUPER_ADMIN, RoleType.ELECTION_ADMIN);
    }

    public UserSession requireVoter() {
        UserSession session = requireAuthenticated();
        if (session.getRole() != RoleType.VOTER) {
            throw new AuthorizationException("Administrators are strictly prohibited from casting voter ballots.");
        }
        if (session.getVoterProfile() == null) {
            throw new AuthorizationException("No voter profile is linked to this account.");
        }
        return session;
    }
}
