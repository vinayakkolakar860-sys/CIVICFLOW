package com.civicflow.security;

import com.civicflow.model.RoleType;
import com.civicflow.model.User;
import com.civicflow.model.Voter;

import java.time.LocalDateTime;

/**
 * Immutable snapshot of the currently authenticated user session.
 */
public class UserSession {
    private final User user;
    private final Voter voterProfile;
    private final LocalDateTime loginTime;

    public UserSession(User user, Voter voterProfile, LocalDateTime loginTime) {
        this.user = user;
        this.voterProfile = voterProfile;
        this.loginTime = loginTime;
    }

    public User getUser() { return user; }
    public long getUserId() { return user.getUserId(); }
    public String getUsername() { return user.getUsername(); }
    public String getFullName() { return user.getFullName(); }
    public RoleType getRole() { return user.getRoleType(); }
    public Voter getVoterProfile() { return voterProfile; }
    public LocalDateTime getLoginTime() { return loginTime; }
    public boolean isMustChangePassword() { return user.isMustChangePassword(); }
}
