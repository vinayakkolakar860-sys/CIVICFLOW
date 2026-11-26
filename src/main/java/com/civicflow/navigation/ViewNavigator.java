package com.civicflow.navigation;

import com.civicflow.model.Election;

/**
 * Callback interface allowing child views to request screen navigation within MainShellView.
 */
public interface ViewNavigator {
    void navigateTo(String routeKey);
    void openVotingBooth(Election election);
    void openResultsForElection(long electionId);
    void handleLogout();
}
