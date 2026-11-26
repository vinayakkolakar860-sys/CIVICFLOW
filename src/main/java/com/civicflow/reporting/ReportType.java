package com.civicflow.reporting;

/**
 * All 11 administrative and simulation reports supported by CIVICFLOW.
 */
public enum ReportType {
    VOTER_REGISTRATION("Voter Registration Report"),
    APPROVED_VOTERS("Approved Voter Report"),
    PENDING_REGISTRATIONS("Pending Registration Report"),
    CANDIDATE_DIRECTORY("Candidate Report"),
    PARTY_DIRECTORY("Fictional Party Report"),
    CONSTITUENCY_SUMMARY("Constituency Report"),
    ELECTION_SCHEDULE("Election Schedule Report"),
    ELECTION_STATUS("Election Status Report"),
    SIMULATED_PARTICIPATION("Simulated Participation Report"),
    PUBLISHED_RESULTS("Published Election Result Report"),
    AUDIT_LOG("Audit Log Report");

    private final String title;

    ReportType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public String toString() {
        return title;
    }
}
