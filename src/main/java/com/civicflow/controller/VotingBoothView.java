package com.civicflow.controller;

import com.civicflow.app.ApplicationContext;
import com.civicflow.model.Candidate;
import com.civicflow.model.Election;
import com.civicflow.model.VotingReceipt;
import com.civicflow.navigation.ViewNavigator;
import com.civicflow.ui.AlertHelper;
import com.civicflow.ui.AsyncTaskRunner;
import com.civicflow.ui.UiComponents;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Simulated Voting Booth screen for authenticated, eligible, verified VOTER accounts.
 * Enforces explicit confirmation and displays a privacy-preserving receipt without candidate choice.
 */
public class VotingBoothView {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApplicationContext ctx;
    private final Election election;
    private final ViewNavigator navigator;
    private final Consumer<Boolean> loadingToggle;

    private final VBox container = new VBox(18);
    private final AtomicReference<Candidate> selectedCandidate = new AtomicReference<>();

    public VotingBoothView(ApplicationContext ctx, Election election, ViewNavigator navigator, Consumer<Boolean> loadingToggle) {
        this.ctx = ctx;
        this.election = election;
        this.navigator = navigator;
        this.loadingToggle = loadingToggle;
    }

    public Node build() {
        AsyncTaskRunner.run(
                () -> ctx.getVotingService().getEligibleCandidatesForBallot(election.getElectionId()),
                this::renderBallot,
                err -> {
                    AlertHelper.showError("Ballot Unavailable", err);
                    navigator.navigateTo(MainShellView.ROUTE_VOTER_DASHBOARD);
                },
                loadingToggle,
                "load simulated ballot"
        );
        return container;
    }

    private void renderBallot(List<Candidate> candidates) {
        container.getChildren().clear();

        HBox banner = UiComponents.createDemoBanner(
                "SIMULATED EDUCATIONAL BALLOT — Your participation and anonymous ballot are stored in separate database tables.");

        // Instructions Card
        VBox instrCard = new VBox(8);
        instrCard.getStyleClass().add("card");
        Label elTitle = new Label(election.getElectionName() + " (" + election.getElectionCode() + ")");
        elTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #182230;");
        Label desc = new Label(election.getDescription() != null && !election.getDescription().isBlank()
                ? election.getDescription()
                : "Select exactly one eligible candidate below and click 'Submit Simulated Ballot'. Once submitted, your vote cannot be altered or cast again.");
        desc.setWrapText(true);
        desc.setStyle("-fx-text-fill: #475569;");

        Label rules = new Label("Voting Rules:  1) One simulated ballot per eligible voter.  2) Submission is final.  3) Receipt confirms participation only (never displays candidate choice).");
        rules.setWrapText(true);
        rules.setStyle("-fx-font-weight: 600; -fx-text-fill: #2855D9; -fx-font-size: 12px;");
        instrCard.getChildren().addAll(elTitle, desc, rules);

        // Candidate Selection List
        VBox ballotCard = new VBox(12);
        ballotCard.getStyleClass().add("card");
        Label cHeading = new Label("Eligible Candidates for Your Constituency");
        cHeading.getStyleClass().add("section-heading");
        ballotCard.getChildren().add(cHeading);

        ToggleGroup toggleGroup = new ToggleGroup();
        VBox cardsHolder = new VBox(10);

        if (candidates.isEmpty()) {
            cardsHolder.getChildren().add(UiComponents.createEmptyState(
                    "No Eligible Candidates", "No approved candidates are assigned to your constituency for this election."));
        } else {
            for (Candidate c : candidates) {
                HBox cCard = new HBox(16);
                cCard.getStyleClass().add("candidate-ballot-card");
                cCard.setAlignment(Pos.CENTER_LEFT);

                RadioButton rb = new RadioButton();
                rb.setToggleGroup(toggleGroup);
                rb.setUserData(c);

                VBox info = new VBox(4);
                HBox.setHgrow(info, Priority.ALWAYS);
                HBox titleLine = new HBox(10);
                titleLine.setAlignment(Pos.CENTER_LEFT);
                Label nameLbl = new Label(c.getFullName());
                nameLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #182230;");
                Label partyBadge = UiComponents.createStatusBadge(c.getPartyCode());
                titleLine.getChildren().addAll(nameLbl, partyBadge);
                if (c.getPartyName() != null && !c.getPartyName().equalsIgnoreCase(c.getPartyCode())) {
                    Label partyName = new Label(c.getPartyName());
                    partyName.setStyle("-fx-text-fill: #697586; -fx-font-size: 12.5px;");
                    titleLine.getChildren().add(partyName);
                }

                Label bioLbl = new Label(c.getProfileDescription() != null && !c.getProfileDescription().isBlank()
                        ? c.getProfileDescription() : "Fictional demonstration candidate (" + c.getCandidateCode() + ").");
                bioLbl.setWrapText(true);
                bioLbl.setStyle("-fx-text-fill: #475569; -fx-font-size: 12px;");

                info.getChildren().addAll(titleLine, bioLbl);
                cCard.getChildren().addAll(rb, info);

                cCard.setOnMouseClicked(e -> rb.setSelected(true));
                rb.selectedProperty().addListener((obs, oldV, isSel) -> {
                    cCard.getStyleClass().remove("candidate-ballot-card-selected");
                    if (isSel) {
                        cCard.getStyleClass().add("candidate-ballot-card-selected");
                        selectedCandidate.set(c);
                    }
                });

                cardsHolder.getChildren().add(cCard);
            }
        }

        // Bottom Action Bar
        Button backBtn = new Button("← Return to Voter Dashboard");
        backBtn.getStyleClass().add("btn-secondary");
        backBtn.setOnAction(e -> navigator.navigateTo(MainShellView.ROUTE_VOTER_DASHBOARD));

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button submitBtn = new Button("Review & Submit Simulated Ballot");
        submitBtn.getStyleClass().add("btn-success");
        submitBtn.setDisable(candidates.isEmpty());
        submitBtn.setOnAction(e -> handleSubmit());

        HBox footerRow = new HBox(12, backBtn, sp, submitBtn);
        footerRow.setPadding(new Insets(8, 0, 0, 0));

        ballotCard.getChildren().addAll(cardsHolder, footerRow);
        container.getChildren().addAll(banner, instrCard, ballotCard);
    }

    private void handleSubmit() {
        Candidate chosen = selectedCandidate.get();
        if (chosen == null) {
            AlertHelper.showWarning("Selection Required", "Please select a candidate before submitting your simulated ballot.");
            return;
        }

        boolean confirmed = AlertHelper.confirm(
                "Confirm Simulated Ballot Submission",
                "Are you ready to submit your simulated ballot?",
                "Selected Candidate: " + chosen.getFullName() + " (" + chosen.getPartyName() + ")\n\n"
                        + "Once confirmed:\n"
                        + "• Your participation will be recorded and locked for this election.\n"
                        + "• Your ballot choice will be stored anonymously without your Voter ID.\n"
                        + "• This action cannot be changed or repeated.");
        if (!confirmed) return;

        AsyncTaskRunner.run(
                () -> ctx.getVotingService().castSimulatedBallot(election.getElectionId(), chosen.getCandidateId()),
                this::showReceiptAndReturn,
                null,
                loadingToggle,
                "submit simulated ballot"
        );
    }

    private void showReceiptAndReturn(VotingReceipt receipt) {
        // Strictly does NOT display the selected candidate name!
        AlertHelper.showSuccess("Simulated Ballot Submitted",
                "Confirmation Reference: " + receipt.getConfirmationCode() + "\n"
                        + "Election: " + receipt.getElectionName() + "\n"
                        + "Recorded At: " + receipt.getSubmittedAt().format(TS_FMT) + "\n\n"
                        + receipt.getMessage());
        navigator.navigateTo(MainShellView.ROUTE_VOTER_DASHBOARD);
    }
}
