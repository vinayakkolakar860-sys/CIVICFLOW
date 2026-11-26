package com.civicflow.controller;

import com.civicflow.app.ApplicationContext;
import com.civicflow.exception.GlobalExceptionHandler;
import com.civicflow.model.*;
import com.civicflow.navigation.ViewNavigator;
import com.civicflow.ui.AlertHelper;
import com.civicflow.ui.AsyncTaskRunner;
import com.civicflow.ui.UiComponents;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Election Management screen supporting full election lifecycle:
 * Create/Edit, Constituency & Candidate assignments, Voter Eligibility sync,
 * Schedule -> Open -> Close -> Publish -> Cancel state transitions.
 */
public class ElectionManagementView {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ApplicationContext ctx;
    private final ViewNavigator navigator;
    private final Consumer<Boolean> loadingToggle;

    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final TableView<Election> table = new TableView<>();

    public ElectionManagementView(ApplicationContext ctx, ViewNavigator navigator, Consumer<Boolean> loadingToggle) {
        this.ctx = ctx;
        this.navigator = navigator;
        this.loadingToggle = loadingToggle;
    }

    public Node build() {
        VBox root = new VBox(16);

        searchField.setPromptText("Search elections by code or title...");
        searchField.setPrefWidth(260);
        searchField.setOnAction(e -> loadElections());

        statusFilter.getItems().setAll("ALL", "DRAFT", "SCHEDULED", "OPEN", "CLOSED", "PUBLISHED", "CANCELLED");
        statusFilter.setValue("ALL");
        statusFilter.setOnAction(e -> loadElections());

        Button searchBtn = new Button("Filter");
        searchBtn.getStyleClass().add("btn-secondary");
        searchBtn.setOnAction(e -> loadElections());

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button createBtn = new Button("+ Create Election");
        createBtn.getStyleClass().add("btn-primary");
        createBtn.setOnAction(e -> openElectionFormDialog(null));

        HBox toolbar = new HBox(10, searchField, statusFilter, searchBtn, sp, createBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // Lifecycle & Assignment Action Row
        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
        editBtn.setOnAction(e -> withSelected(this::openElectionFormDialog));

        Button assignBtn = new Button("Constituencies & Candidates");
        assignBtn.getStyleClass().addAll("btn-accent", "btn-sm");
        assignBtn.setOnAction(e -> withSelected(this::openAssignmentsDialog));

        Button scheduleBtn = new Button("Schedule");
        scheduleBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
        scheduleBtn.setOnAction(e -> withSelected(el -> transition(el, "Schedule Election",
                "Move '" + el.getElectionName() + "' to SCHEDULED and sync eligible voters?",
                () -> ctx.getElectionService().scheduleElection(el.getElectionId()), "Election scheduled.")));

        Button openBtn = new Button("Open Voting");
        openBtn.getStyleClass().addAll("btn-success", "btn-sm");
        openBtn.setOnAction(e -> withSelected(el -> transition(el, "Open Election",
                "Activate '" + el.getElectionName() + "' and allow eligible voters to cast simulated ballots?",
                () -> ctx.getElectionService().openElection(el.getElectionId()), "Election is now OPEN.")));

        Button closeBtn = new Button("Close Voting");
        closeBtn.getStyleClass().addAll("btn-warning", "btn-sm");
        closeBtn.setOnAction(e -> withSelected(el -> transition(el, "Close Election",
                "Close '" + el.getElectionName() + "'? No further ballots will be accepted.",
                () -> ctx.getElectionService().closeElection(el.getElectionId()), "Election CLOSED.")));

        Button publishBtn = new Button("Publish Results");
        publishBtn.getStyleClass().addAll("btn-primary", "btn-sm");
        publishBtn.setOnAction(e -> withSelected(el -> transition(el, "Publish Results",
                "Publish final simulated results for '" + el.getElectionName() + "'? Published elections become immutable.",
                () -> ctx.getElectionService().publishResults(el.getElectionId()), "Results PUBLISHED.")));

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().addAll("btn-danger", "btn-sm");
        cancelBtn.setOnAction(e -> withSelected(el -> transition(el, "Cancel Election",
                "Cancel '" + el.getElectionName() + "'? This action cannot be undone.",
                () -> ctx.getElectionService().cancelElection(el.getElectionId()), "Election CANCELLED.")));

        Button resultsBtn = new Button("View Results");
        resultsBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
        resultsBtn.setOnAction(e -> withSelected(el -> {
            if (el.getStatus() != ElectionStatus.CLOSED && el.getStatus() != ElectionStatus.PUBLISHED) {
                AlertHelper.showWarning("Results Unavailable", "Close the election first before viewing tallied results.");
                return;
            }
            navigator.openResultsForElection(el.getElectionId());
        }));

        FlowPane actionBar = new FlowPane(8, 8, editBtn, assignBtn, scheduleBtn, openBtn, closeBtn, publishBtn, resultsBtn, cancelBtn);
        actionBar.setAlignment(Pos.CENTER_LEFT);

        configureTable();
        VBox card = new VBox(12, toolbar, actionBar, table);
        card.getStyleClass().add("card");
        root.getChildren().add(card);

        loadElections();
        return root;
    }

    private void configureTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(480);
        table.setPlaceholder(UiComponents.createEmptyState("No Elections Found", "Create a new simulated election to begin."));

        TableColumn<Election, String> cCode = new TableColumn<>("Code");
        cCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getElectionCode()));
        TableColumn<Election, String> cName = new TableColumn<>("Election Name");
        cName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getElectionName()));
        TableColumn<Election, String> cType = new TableColumn<>("Type");
        cType.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getElectionType().getLabel()));
        TableColumn<Election, String> cStart = new TableColumn<>("Start");
        cStart.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStartDateTime().format(DT_FMT)));
        TableColumn<Election, String> cEnd = new TableColumn<>("End");
        cEnd.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEndDateTime().format(DT_FMT)));
        TableColumn<Election, String> cCounts = new TableColumn<>("Const / Cand / Votes");
        cCounts.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getConstituencyCount() + " / " + d.getValue().getCandidateCount() + " / " + d.getValue().getTotalParticipants()));
        TableColumn<Election, String> cStatus = new TableColumn<>("Status");
        cStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus().name()));
        cStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : UiComponents.createStatusBadge(item));
            }
        });

        table.getColumns().addAll(cCode, cName, cType, cStart, cEnd, cCounts, cStatus);
    }

    private void loadElections() {
        ElectionStatus st = "ALL".equals(statusFilter.getValue()) ? null : ElectionStatus.valueOf(statusFilter.getValue());
        AsyncTaskRunner.run(
                () -> ctx.getElectionService().getAllElections(searchField.getText(), st),
                list -> table.setItems(FXCollections.observableArrayList(list)),
                null, loadingToggle, "load elections"
        );
    }

    private void withSelected(Consumer<Election> fn) {
        Election sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { AlertHelper.showWarning("No Selection", "Please select an election row first."); return; }
        fn.accept(sel);
    }

    private void transition(Election el, String title, String msg, Runnable work, String success) {
        if (!AlertHelper.confirm(title, title + " — " + el.getElectionCode(), msg)) return;
        AsyncTaskRunner.run(
                () -> { work.run(); return null; },
                ignored -> { AlertHelper.showSuccess("Status Updated", success); loadElections(); },
                null, loadingToggle, title
        );
    }

    private void openElectionFormDialog(Election existing) {
        boolean isEdit = existing != null;
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Election" : "Create Simulated Election");
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField nameField = new TextField(isEdit ? existing.getElectionName() : "");
        nameField.setPrefWidth(320);
        ComboBox<ElectionType> typeBox = new ComboBox<>(FXCollections.observableArrayList(ElectionType.values()));
        typeBox.setValue(isEdit ? existing.getElectionType() : ElectionType.GENERAL);

        DatePicker startDate = new DatePicker(isEdit ? existing.getStartDateTime().toLocalDate() : LocalDate.now());
        TextField startTime = new TextField(isEdit ? existing.getStartDateTime().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "08:00");
        startTime.setPrefWidth(90);

        DatePicker endDate = new DatePicker(isEdit ? existing.getEndDateTime().toLocalDate() : LocalDate.now().plusDays(7));
        TextField endTime = new TextField(isEdit ? existing.getEndDateTime().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "18:00");
        endTime.setPrefWidth(90);

        TextArea desc = new TextArea(isEdit && existing.getDescription() != null ? existing.getDescription() : "");
        desc.setPrefRowCount(3);
        desc.setWrapText(true);

        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill: #DC4C4C; -fx-font-weight: bold;");
        errLabel.setWrapText(true);

        grid.addRow(0, new Label("Election Name*:"), nameField);
        grid.addRow(1, new Label("Election Type*:"), typeBox);
        grid.addRow(2, new Label("Start Date & Time (HH:mm)*:"), new HBox(8, startDate, startTime));
        grid.addRow(3, new Label("End Date & Time (HH:mm)*:"), new HBox(8, endDate, endTime));
        grid.addRow(4, new Label("Description / Instructions:"), desc);
        grid.add(errLabel, 0, 5, 2, 1);
        dialog.getDialogPane().setContent(grid);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            try {
                if (startDate.getValue() == null || endDate.getValue() == null) {
                    throw new com.civicflow.exception.ValidationException("Start and end dates are required.");
                }
                LocalTime st = LocalTime.parse(startTime.getText().trim(), DateTimeFormatter.ofPattern("H:mm"));
                LocalTime et = LocalTime.parse(endTime.getText().trim(), DateTimeFormatter.ofPattern("H:mm"));
                LocalDateTime startDt = LocalDateTime.of(startDate.getValue(), st);
                LocalDateTime endDt = LocalDateTime.of(endDate.getValue(), et);

                Election el = isEdit ? existing : new Election();
                el.setElectionName(nameField.getText());
                el.setElectionType(typeBox.getValue());
                el.setStartDateTime(startDt);
                el.setEndDateTime(endDt);
                el.setDescription(desc.getText());

                if (isEdit) ctx.getElectionService().updateElection(el);
                else ctx.getElectionService().createElection(el);
            } catch (java.time.format.DateTimeParseException dtEx) {
                errLabel.setText("Invalid time format. Use HH:mm (e.g., 09:00 or 17:30).");
                evt.consume();
            } catch (Exception ex) {
                errLabel.setText(GlobalExceptionHandler.handleAndGetUserMessage(ex, "save election"));
                evt.consume();
            }
        });

        dialog.setResultConverter(b -> b == saveType);
        if (dialog.showAndWait().orElse(false)) {
            AlertHelper.showSuccess("Saved", isEdit ? "Election updated." : "Election created in DRAFT status.");
            loadElections();
        }
    }

    private void openAssignmentsDialog(Election election) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Manage Assignments — " + election.getElectionCode());
        dialog.setHeaderText(election.getElectionName() + " [" + election.getStatus() + "]");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        boolean editable = election.getStatus() == ElectionStatus.DRAFT || election.getStatus() == ElectionStatus.SCHEDULED;

        ListView<Constituency> assignedConstList = new ListView<>();
        assignedConstList.setPrefSize(320, 210);
        ComboBox<Constituency> allConstCombo = new ComboBox<>();
        allConstCombo.setPrefWidth(220);

        ListView<Candidate> assignedCandList = new ListView<>();
        assignedCandList.setPrefSize(360, 210);
        ComboBox<Candidate> approvedCandCombo = new ComboBox<>();
        approvedCandCombo.setPrefWidth(260);

        Runnable reloadLists = () -> {
            List<Constituency> allC = ctx.getConstituencyService().getAll(null, ConstituencyStatus.ACTIVE);
            List<Constituency> assignedC = ctx.getElectionService().getAssignedConstituencies(election.getElectionId());
            List<Candidate> allApprovedCands = ctx.getCandidateService().search(null, null, null, CandidateStatus.APPROVED);
            List<Candidate> assignedCands = ctx.getElectionService().getAssignedCandidates(election.getElectionId(), null);

            allConstCombo.setItems(FXCollections.observableArrayList(allC));
            assignedConstList.setItems(FXCollections.observableArrayList(assignedC));
            approvedCandCombo.setItems(FXCollections.observableArrayList(allApprovedCands));
            assignedCandList.setItems(FXCollections.observableArrayList(assignedCands));
        };
        reloadLists.run();

        Button addConstBtn = new Button("Assign");
        addConstBtn.getStyleClass().addAll("btn-primary", "btn-sm");
        addConstBtn.setDisable(!editable);
        addConstBtn.setOnAction(e -> {
            if (allConstCombo.getValue() == null) return;
            try {
                ctx.getElectionService().assignConstituency(election.getElectionId(), allConstCombo.getValue().getConstituencyId());
                reloadLists.run();
            } catch (Exception ex) {
                AlertHelper.showError("Assignment Error", GlobalExceptionHandler.handleAndGetUserMessage(ex, "assign constituency"));
            }
        });

        Button remConstBtn = new Button("Remove Selected");
        remConstBtn.getStyleClass().addAll("btn-danger", "btn-sm");
        remConstBtn.setDisable(!editable);
        remConstBtn.setOnAction(e -> {
            Constituency sel = assignedConstList.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            try {
                ctx.getElectionService().removeConstituency(election.getElectionId(), sel.getConstituencyId());
                reloadLists.run();
            } catch (Exception ex) {
                AlertHelper.showError("Error", GlobalExceptionHandler.handleAndGetUserMessage(ex, "remove constituency"));
            }
        });

        Button addCandBtn = new Button("Assign");
        addCandBtn.getStyleClass().addAll("btn-primary", "btn-sm");
        addCandBtn.setDisable(!editable);
        addCandBtn.setOnAction(e -> {
            if (approvedCandCombo.getValue() == null) return;
            try {
                ctx.getElectionService().assignCandidate(election.getElectionId(), approvedCandCombo.getValue().getCandidateId());
                reloadLists.run();
            } catch (Exception ex) {
                AlertHelper.showError("Assignment Error", GlobalExceptionHandler.handleAndGetUserMessage(ex, "assign candidate"));
            }
        });

        Button remCandBtn = new Button("Remove Selected");
        remCandBtn.getStyleClass().addAll("btn-danger", "btn-sm");
        remCandBtn.setDisable(!editable);
        remCandBtn.setOnAction(e -> {
            Candidate sel = assignedCandList.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            try {
                ctx.getElectionService().removeCandidate(election.getElectionId(), sel.getCandidateId());
                reloadLists.run();
            } catch (Exception ex) {
                AlertHelper.showError("Error", GlobalExceptionHandler.handleAndGetUserMessage(ex, "remove candidate"));
            }
        });

        Button syncEligBtn = new Button("Sync Approved Voter Eligibility");
        syncEligBtn.getStyleClass().addAll("btn-accent", "btn-sm");
        syncEligBtn.setDisable(election.getStatus() == ElectionStatus.CLOSED || election.getStatus() == ElectionStatus.PUBLISHED || election.getStatus() == ElectionStatus.CANCELLED);
        syncEligBtn.setOnAction(e -> {
            try {
                int synced = ctx.getElectionService().syncEligibility(election.getElectionId());
                AlertHelper.showSuccess("Eligibility Synchronized", "Eligible voter records synchronized (" + synced + " rows processed).");
            } catch (Exception ex) {
                AlertHelper.showError("Sync Error", GlobalExceptionHandler.handleAndGetUserMessage(ex, "sync voter eligibility"));
            }
        });

        VBox leftCol = new VBox(8,
                new Label("1. Assigned Constituencies"),
                new HBox(8, allConstCombo, addConstBtn),
                assignedConstList,
                remConstBtn);
        VBox rightCol = new VBox(8,
                new Label("2. Assigned Approved Candidates"),
                new HBox(8, approvedCandCombo, addCandBtn),
                assignedCandList,
                remCandBtn);

        VBox content = new VBox(14,
                new HBox(18, leftCol, rightCol),
                syncEligBtn);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
        loadElections();
    }
}
