package com.civicflow.controller;

import com.civicflow.app.ApplicationContext;
import com.civicflow.model.*;
import com.civicflow.navigation.ViewNavigator;
import com.civicflow.ui.AsyncTaskRunner;
import com.civicflow.ui.UiComponents;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Voter Portal Dashboard displaying profile & verification status, eligible simulated elections,
 * non-revealing participation history, and published results access.
 */
public class VoterDashboardView {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ApplicationContext ctx;
    private final ViewNavigator navigator;
    private final Consumer<Boolean> loadingToggle;
    private final VBox container = new VBox(18);

    public VoterDashboardView(ApplicationContext ctx, ViewNavigator navigator, Consumer<Boolean> loadingToggle) {
        this.ctx = ctx;
        this.navigator = navigator;
        this.loadingToggle = loadingToggle;
    }

    public Node build() {
        refresh();
        return container;
    }

    private record VoterDashboardPayload(Voter voter, List<Election> eligibleElections,
                                         List<VoterParticipation> participationList) {}

    private void refresh() {
        AsyncTaskRunner.run(
                () -> {
                    Voter voter = ctx.getVoterService().getOwnVoterProfile();
                    List<Election> elections = ctx.getElectionService().getEligibleElectionsForCurrentVoter();
                    List<VoterParticipation> history = ctx.getVoterService().getParticipationHistory(voter.getVoterId());
                    return new VoterDashboardPayload(voter, elections, history);
                },
                this::render,
                null,
                loadingToggle,
                "load voter dashboard"
        );
    }

    private void render(VoterDashboardPayload payload) {
        container.getChildren().clear();
        Voter voter = payload.voter();
        Set<Long> participatedIds = new HashSet<>();
        for (VoterParticipation vp : payload.participationList()) {
            participatedIds.add(vp.getElectionId());
        }

        HBox banner = UiComponents.createDemoBanner(
                "EDUCATIONAL VOTING SIMULATION — Votes cast here are simulated demonstration ballots stored separately from your voter profile.");

        // Voter Identity & Status Summary Card
        HBox profileRow = new HBox(14,
                UiComponents.createStatCard("Voter ID", voter.getVoterCode(), "#2855D9"),
                UiComponents.createStatCard("Constituency", voter.getConstituencyName(), "#17A6A1"),
                UiComponents.createStatCard("Verification", voter.getVerificationStatus().name(),
                        voter.getVerificationStatus() == VerificationStatus.APPROVED ? "#16A36A" : "#E9A23B"),
                UiComponents.createStatCard("Elections Participated", String.valueOf(payload.participationList().size()), "#6366F1")
        );

        // Eligible Elections Table
        TableView<Election> electionTable = new TableView<>();
        electionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        electionTable.setPrefHeight(270);
        electionTable.setPlaceholder(UiComponents.createEmptyState(
                "No Eligible Elections Found",
                "Once an administrator schedules or opens an election covering your constituency, it will appear here."));

        TableColumn<Election, String> colCode = new TableColumn<>("Code");
        colCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getElectionCode()));
        colCode.setPrefWidth(110);

        TableColumn<Election, String> colName = new TableColumn<>("Election Name");
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getElectionName()));
        colName.setPrefWidth(230);

        TableColumn<Election, String> colWindow = new TableColumn<>("Voting Window");
        colWindow.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getStartDateTime().format(DT_FMT) + " → " + d.getValue().getEndDateTime().format(DT_FMT)));
        colWindow.setPrefWidth(230);

        TableColumn<Election, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus().name()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : UiComponents.createStatusBadge(item));
            }
        });
        colStatus.setPrefWidth(110);

        TableColumn<Election, String> colPart = new TableColumn<>("Your Participation");
        colPart.setCellValueFactory(d -> new SimpleStringProperty(
                participatedIds.contains(d.getValue().getElectionId()) ? "SUBMITTED" : "NOT VOTED"));
        colPart.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : UiComponents.createStatusBadge(item));
            }
        });
        colPart.setPrefWidth(130);

        TableColumn<Election, Void> colAction = new TableColumn<>("Action");
        colAction.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                Election election = getTableRow().getItem();
                boolean alreadyVoted = participatedIds.contains(election.getElectionId());
                if (election.getStatus() == ElectionStatus.OPEN && !alreadyVoted) {
                    Button voteBtn = new Button("Cast Simulated Vote");
                    voteBtn.getStyleClass().addAll("btn-success", "btn-sm");
                    voteBtn.setDisable(voter.getVerificationStatus() != VerificationStatus.APPROVED);
                    voteBtn.setOnAction(e -> navigator.openVotingBooth(election));
                    setGraphic(voteBtn);
                } else if (election.getStatus() == ElectionStatus.PUBLISHED) {
                    Button resBtn = new Button("View Results");
                    resBtn.getStyleClass().addAll("btn-primary", "btn-sm");
                    resBtn.setOnAction(e -> navigator.openResultsForElection(election.getElectionId()));
                    setGraphic(resBtn);
                } else if (alreadyVoted) {
                    Label lbl = new Label("Recorded");
                    lbl.setStyle("-fx-text-fill: #15803D; -fx-font-weight: bold; -fx-font-size: 12px;");
                    setGraphic(lbl);
                } else {
                    Label lbl = new Label("Awaiting Schedule");
                    lbl.setStyle("-fx-text-fill: #697586; -fx-font-size: 12px;");
                    setGraphic(lbl);
                }
            }
        });
        colAction.setPrefWidth(160);

        electionTable.getColumns().addAll(colCode, colName, colWindow, colStatus, colPart, colAction);
        electionTable.setItems(FXCollections.observableArrayList(payload.eligibleElections()));

        HBox electionHead = new HBox(10);
        electionHead.setAlignment(Pos.CENTER_LEFT);
        Label elTitle = new Label("Your Eligible Simulated Elections");
        elTitle.getStyleClass().add("section-heading");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
        refreshBtn.setOnAction(e -> refresh());
        electionHead.getChildren().addAll(elTitle, sp, refreshBtn);

        VBox electionsCard = new VBox(12, electionHead, electionTable);
        electionsCard.getStyleClass().add("card");

        // Participation History Table (NO ballot choices ever stored or displayed!)
        TableView<VoterParticipation> historyTable = new TableView<>();
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        historyTable.setPrefHeight(200);
        historyTable.setPlaceholder(UiComponents.createEmptyState("No Participation Recorded Yet",
                "After submitting a simulated ballot, your participation confirmation (without candidate choice) will appear here."));

        TableColumn<VoterParticipation, String> hCode = new TableColumn<>("Election Code");
        hCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getElectionCode()));
        TableColumn<VoterParticipation, String> hName = new TableColumn<>("Election Name");
        hName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getElectionName()));
        TableColumn<VoterParticipation, String> hTime = new TableColumn<>("Participation Recorded At");
        hTime.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getSubmittedAt() != null ? d.getValue().getSubmittedAt().format(DT_FMT) : ""));
        TableColumn<VoterParticipation, String> hStatus = new TableColumn<>("Status");
        hStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getParticipationStatus()));
        hStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : UiComponents.createStatusBadge(item));
            }
        });
        historyTable.getColumns().addAll(hCode, hName, hTime, hStatus);
        historyTable.setItems(FXCollections.observableArrayList(payload.participationList()));

        VBox historyCard = UiComponents.wrapInCard("Participation Log (Ballot Selections Are Never Stored Here)", historyTable);

        container.getChildren().addAll(banner, profileRow, electionsCard, historyCard);
    }
}
