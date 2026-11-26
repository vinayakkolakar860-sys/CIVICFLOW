package com.civicflow.model;

public enum ElectionType {
    GENERAL("General Simulation"),
    LOCAL("Local District"),
    BY_ELECTION("By-Election"),
    CAMPUS("Campus Council"),
    COUNCIL("Civic Council"),
    MOCK_POLL("Mock Poll");

    private final String label;

    ElectionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
