package com.civicflow.model;

public enum RoleType {
    SUPER_ADMIN("Super Admin"),
    ELECTION_ADMIN("Election Admin"),
    VOTER("Voter");

    private final String displayName;

    RoleType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isAdmin() {
        return this == SUPER_ADMIN || this == ELECTION_ADMIN;
    }
}
