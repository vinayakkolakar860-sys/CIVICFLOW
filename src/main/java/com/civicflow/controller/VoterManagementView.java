package com.civicflow.controller;

import com.civicflow.app.ApplicationContext;
import com.civicflow.exception.GlobalExceptionHandler;
import com.civicflow.model.*;
import com.civicflow.reporting.ReportData;
import com.civicflow.reporting.ReportType;
import com.civicflow.security.CsvSanitizer;
import com.civicflow.service.SettingsService;
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
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Full Voter Management screen: search, filter, pagination, registration, editing,
 * approval/rejection, suspension/reactivation, profile & participation history, and export.
 */
public class VoterManagementView {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ApplicationContext ctx;
    private final Consumer<Boolean> loadingToggle;

    private final TextField searchField = new TextField();
    private final ComboBox<String> verifFilter = new ComboBox<>();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<Constituency> constituencyFilter = new ComboBox<>();
    private final TableView<Voter> table = new TableView<>();
    private final UiComponents.PaginationBar paginationBar = new UiComponents.PaginationBar();
    private List<Constituency> constituencies = List.of();

    public VoterManagementView(ApplicationContext ctx, Consumer<Boolean> loadingToggle) {
        this.ctx = ctx;
        this.loadingToggle = loadingToggle;
    }

    public Node build() {
        VBox root = new VBox(16);

        // Filter & Action Toolbar
        searchField.setPromptText("Search by voter ID, name, email, username...");
        searchField.setPrefWidth(260);
        searchField.setOnAction(e -> { paginationBar.resetToFirstPage(); loadPage(); });

        verifFilter.getItems().setAll("ALL", "PENDING", "APPROVED", "REJECTED");
        verifFilter.setValue("ALL");
        verifFilter.setOnAction(e -> { paginationBar.resetToFirstPage(); loadPage(); });

        statusFilter.getItems().setAll("ALL", "ACTIVE", "SUSPENDED", "LOCKED", "INACTIVE");
        statusFilter.setValue("ALL");
        statusFilter.setOnAction(e -> { paginationBar.resetToFirstPage(); loadPage(); });

        constituencyFilter.setPromptText("All Constituencies");
        constituencyFilter.setPrefWidth(200);
        constituencyFilter.setOnAction(e -> { paginationBar.resetToFirstPage(); loadPage(); });

        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().add("btn-secondary");
        searchBtn.setOnAction(e -> { paginationBar.resetToFirstPage(); loadPage(); });

        Button clearBtn = new Button("Reset");
        clearBtn.getStyleClass().add("btn-secondary");
        clearBtn.setOnAction(e -> {
            searchField.clear();
            verifFilter.setValue("ALL");
            statusFilter.setValue("ALL");
            constituencyFilter.setValue(null);
            paginationBar.resetToFirstPage();
            loadPage();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button registerBtn = new Button("+ Register Voter");
        registerBtn.getStyleClass().add("btn-primary");
        registerBtn.setOnAction(e -> openVoterFormDialog(null));

        Button exportCsvBtn = new Button("Export CSV");
        exportCsvBtn.getStyleClass().add("btn-secondary");
        exportCsvBtn.setOnAction(e -> exportVoters(false));

        Button exportPdfBtn = new Button("Export PDF");
        exportPdfBtn.getStyleClass().add("btn-accent");
        exportPdfBtn.setOnAction(e -> exportVoters(true));

        HBox toolbar = new HBox(10, searchField, verifFilter, statusFilter, constituencyFilter, searchBtn, clearBtn, spacer, exportCsvBtn, exportPdfBtn, registerBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // Action Bar for Selected Row
        Button editBtn = new Button("Edit Details");
        editBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
        editBtn.setOnAction(e -> withSelected(this::openVoterFormDialog));

        Button profileBtn = new Button("View Profile & Participation");
        profileBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
        profileBtn.setOnAction(e -> withSelected(this::openProfileDialog));

        Button approveBtn = new Button("Approve");
        approveBtn.getStyleClass().addAll("btn-success", "btn-sm");
        approveBtn.setOnAction(e -> withSelected(v -> runAction(
                "Approve Voter", "Approve registration for " + v.getFullName() + " (" + v.getVoterCode() + ")?",
                () -> ctx.getVoterService().approveVoter(v.getVoterId()), "Voter approved.")));

        Button rejectBtn = new Button("Reject");
        rejectBtn.getStyleClass().addAll("btn-warning", "btn-sm");
        rejectBtn.setOnAction(e -> withSelected(v -> runAction(
                "Reject Voter", "Reject registration for " + v.getFullName() + " (" + v.getVoterCode() + ")?",
                () -> ctx.getVoterService().rejectVoter(v.getVoterId()), "Voter rejected.")));

        Button suspendBtn = new Button("Suspend");
        suspendBtn.getStyleClass().addAll("btn-danger", "btn-sm");
        suspendBtn.setOnAction(e -> withSelected(v -> runAction(
                "Suspend Voter Account", "Suspend account for " + v.getFullName() + "? They will not be able to log in or vote.",
                () -> ctx.getVoterService().suspendVoter(v.getVoterId()), "Voter account suspended.")));

        Button reactivateBtn = new Button("Reactivate");
        reactivateBtn.getStyleClass().addAll("btn-accent", "btn-sm");
        reactivateBtn.setOnAction(e -> withSelected(v -> runAction(
                "Reactivate Voter Account", "Reactivate account for " + v.getFullName() + "?",
                () -> ctx.getVoterService().reactivateVoter(v.getVoterId()), "Voter account reactivated.")));

        HBox rowActions = new HBox(8, new Label("Selected Voter:"), profileBtn, editBtn, approveBtn, rejectBtn, suspendBtn, reactivateBtn);
        rowActions.setAlignment(Pos.CENTER_LEFT);

        // Table
        configureTable();
        paginationBar.setOnPageChange(this::loadPage);

        VBox tableCard = new VBox(12, toolbar, rowActions, table, paginationBar);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(table, Priority.ALWAYS);

        root.getChildren().addAll(
                UiComponents.createDemoBanner("FICTIONAL DEMO VOTERS ONLY — Do not enter real citizen government IDs or personal identity documents."),
                tableCard
        );

        loadConstituenciesAndPage();
        return root;
    }

    private void configureTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(460);
        table.setPlaceholder(UiComponents.createEmptyState("No Voters Found", "Adjust filters or register a new fictional voter."));

        TableColumn<Voter, String> cCode = new TableColumn<>("Voter ID");
        cCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getVoterCode()));
        cCode.setPrefWidth(130);

        TableColumn<Voter, String> cName = new TableColumn<>("Full Name");
        cName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFullName()));
        cName.setPrefWidth(160);

        TableColumn<Voter, String> cUser = new TableColumn<>("Username");
        cUser.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUsername()));
        cUser.setPrefWidth(120);

        TableColumn<Voter, String> cEmail = new TableColumn<>("Email");
        cEmail.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEmail()));
        cEmail.setPrefWidth(190);

        TableColumn<Voter, String> cConst = new TableColumn<>("Constituency");
        cConst.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getConstituencyName()));
        cConst.setPrefWidth(160);

        TableColumn<Voter, String> cVerif = new TableColumn<>("Verification");
        cVerif.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getVerificationStatus().name()));
        cVerif.setCellFactory(col -> badgeCell());
        cVerif.setPrefWidth(110);

        TableColumn<Voter, String> cAcct = new TableColumn<>("Account");
        cAcct.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAccountStatus().name()));
        cAcct.setCellFactory(col -> badgeCell());
        cAcct.setPrefWidth(100);

        table.getColumns().addAll(cCode, cName, cUser, cEmail, cConst, cVerif, cAcct);
    }

    private TableCell<Voter, String> badgeCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : UiComponents.createStatusBadge(item));
            }
        };
    }

    private void loadConstituenciesAndPage() {
        AsyncTaskRunner.run(
                () -> ctx.getConstituencyService().getAll(null, ConstituencyStatus.ACTIVE),
                list -> {
                    constituencies = list;
                    constituencyFilter.getItems().setAll(list);
                    loadPage();
                },
                null,
                loadingToggle,
                "load constituencies"
        );
    }

    private void loadPage() {
        VerificationStatus vs = "ALL".equals(verifFilter.getValue()) ? null : VerificationStatus.valueOf(verifFilter.getValue());
        AccountStatus as = "ALL".equals(statusFilter.getValue()) ? null : AccountStatus.valueOf(statusFilter.getValue());
        Long constId = constituencyFilter.getValue() != null ? constituencyFilter.getValue().getConstituencyId() : null;
        int pageSize = ctx.getSettingsService().getIntSetting(SettingsService.KEY_PAGE_SIZE, 25);

        AsyncTaskRunner.run(
                () -> ctx.getVoterService().searchVoters(searchField.getText(), vs, as, constId, paginationBar.getCurrentPage(), pageSize),
                page -> {
                    table.setItems(FXCollections.observableArrayList(page.getItems()));
                    paginationBar.update(page.getPageNumber(), page.getTotalPages(), page.getTotalCount());
                },
                null,
                loadingToggle,
                "search voters"
        );
    }

    private void withSelected(Consumer<Voter> action) {
        Voter sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AlertHelper.showWarning("No Selection", "Please select a voter row from the table first.");
            return;
        }
        action.accept(sel);
    }

    private void runAction(String title, String prompt, Runnable work, String successMsg) {
        if (!AlertHelper.confirm(title, title, prompt)) return;
        AsyncTaskRunner.run(
                () -> { work.run(); return null; },
                ignored -> {
                    AlertHelper.showSuccess("Success", successMsg);
                    loadPage();
                },
                null,
                loadingToggle,
                title
        );
    }

    private void openVoterFormDialog(Voter existing) {
        boolean isEdit = existing != null;
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Fictional Voter" : "Register New Fictional Voter");
        dialog.setHeaderText(isEdit ? "Update voter details for " + existing.getVoterCode()
                : "Create a fictional voter profile and linked voter login account.");

        ButtonType saveType = new ButtonType(isEdit ? "Save Changes" : "Register Voter", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField firstName = new TextField(isEdit ? existing.getFirstName() : "");
        TextField lastName = new TextField(isEdit ? existing.getLastName() : "");
        TextField email = new TextField(isEdit ? existing.getEmail() : "");
        TextField phone = new TextField(isEdit ? existing.getPhoneNumber() : "");
        DatePicker dob = new DatePicker(isEdit ? existing.getDateOfBirth() : LocalDate.of(1998, 1, 15));
        TextField address = new TextField(isEdit ? existing.getAddress() : "");
        TextField city = new TextField(isEdit ? existing.getCity() : "");
        TextField state = new TextField(isEdit ? existing.getState() : "");
        ComboBox<Constituency> constBox = new ComboBox<>(FXCollections.observableArrayList(constituencies));
        constBox.setPrefWidth(260);
        if (isEdit) {
            constituencies.stream().filter(c -> c.getConstituencyId() == existing.getConstituencyId()).findFirst().ifPresent(constBox::setValue);
        } else if (!constituencies.isEmpty()) {
            constBox.setValue(constituencies.get(0));
        }

        TextField usernameField = new TextField();
        usernameField.setPromptText("e.g., voter.alex");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Min 8 chars, upper/lower/digit/symbol");

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #DC4C4C; -fx-font-weight: bold;");
        errorLabel.setWrapText(true);

        int r = 0;
        grid.addRow(r++, new Label("First Name*:"), firstName, new Label("Last Name*:"), lastName);
        grid.addRow(r++, new Label("Email*:"), email, new Label("Phone*:"), phone);
        grid.addRow(r++, new Label("Date of Birth*:"), dob, new Label("Constituency*:"), constBox);
        grid.addRow(r++, new Label("Address*:"), address, new Label("City*:"), city);
        grid.addRow(r++, new Label("State*:"), state);
        if (!isEdit) {
            grid.addRow(r++, new Label("Login Username*:"), usernameField, new Label("Initial Password*:"), passwordField);
        }
        grid.add(errorLabel, 0, r, 4, 1);
        dialog.getDialogPane().setContent(grid);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            try {
                Voter v = isEdit ? existing : new Voter();
                v.setFirstName(firstName.getText());
                v.setLastName(lastName.getText());
                v.setEmail(email.getText());
                v.setPhoneNumber(phone.getText());
                v.setDateOfBirth(dob.getValue());
                v.setAddress(address.getText());
                v.setCity(city.getText());
                v.setState(state.getText());
                if (constBox.getValue() == null) {
                    throw new com.civicflow.exception.ValidationException("Please select a constituency.");
                }
                v.setConstituencyId(constBox.getValue().getConstituencyId());

                if (isEdit) {
                    ctx.getVoterService().updateVoter(v);
                } else {
                    ctx.getVoterService().registerVoter(v, usernameField.getText(), passwordField.getText());
                }
            } catch (Exception ex) {
                errorLabel.setText(GlobalExceptionHandler.handleAndGetUserMessage(ex, "save voter"));
                evt.consume();
            }
        });

        dialog.setResultConverter(btn -> btn == saveType);
        if (dialog.showAndWait().orElse(false)) {
            AlertHelper.showSuccess("Saved", isEdit ? "Voter updated successfully." : "Fictional voter registered successfully.");
            loadPage();
        }
    }

    private void openProfileDialog(Voter voter) {
        AsyncTaskRunner.run(
                () -> ctx.getVoterService().getParticipationHistory(voter.getVoterId()),
                history -> {
                    Dialog<Void> dialog = new Dialog<>();
                    dialog.setTitle("Voter Profile — " + voter.getVoterCode());
                    dialog.setHeaderText(voter.getFullName() + "  (" + voter.getVoterCode() + ")");
                    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

                    VBox body = new VBox(12);
                    body.setPrefWidth(540);
                    Label info = new Label(String.format(
                            "Username: %s\nEmail: %s  |  Phone: %s\nDOB: %s  |  Registered: %s\nAddress: %s, %s, %s\nConstituency: %s (%s)\nVerification: %s  |  Account: %s",
                            voter.getUsername(), voter.getEmail(), voter.getPhoneNumber(),
                            voter.getDateOfBirth(), voter.getRegistrationDate(),
                            voter.getAddress(), voter.getCity(), voter.getState(),
                            voter.getConstituencyName(), voter.getConstituencyCode(),
                            voter.getVerificationStatus(), voter.getAccountStatus()));
                    info.setWrapText(true);

                    TableView<VoterParticipation> pTable = new TableView<>();
                    pTable.setPrefHeight(180);
                    pTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
                    pTable.setPlaceholder(new Label("No simulated election participation recorded yet."));
                    TableColumn<VoterParticipation, String> c1 = new TableColumn<>("Election");
                    c1.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getElectionName()));
                    TableColumn<VoterParticipation, String> c2 = new TableColumn<>("Submitted At");
                    c2.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSubmittedAt().format(DT_FMT)));
                    TableColumn<VoterParticipation, String> c3 = new TableColumn<>("Status");
                    c3.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getParticipationStatus()));
                    pTable.getColumns().addAll(c1, c2, c3);
                    pTable.setItems(FXCollections.observableArrayList(history));

                    Label privacyNote = new Label("Note: Ballot selections are strictly excluded from voter profiles and participation records.");
                    privacyNote.setStyle("-fx-font-style: italic; -fx-text-fill: #697586; -fx-font-size: 11.5px;");

                    body.getChildren().addAll(info, new Label("Election Participation History:"), pTable, privacyNote);
                    dialog.getDialogPane().setContent(body);
                    dialog.showAndWait();
                },
                null,
                loadingToggle,
                "load voter participation"
        );
    }

    private void exportVoters(boolean pdf) {
        FileChooser fc = new FileChooser();
        fc.setTitle(pdf ? "Export Voter List (PDF)" : "Export Voter List (CSV)");
        fc.setInitialFileName(CsvSanitizer.sanitizeFileName("civicflow_voters", pdf ? ".pdf" : ".csv"));
        fc.getExtensionFilters().add(pdf
                ? new FileChooser.ExtensionFilter("PDF Documents", "*.pdf")
                : new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showSaveDialog(table.getScene().getWindow());
        if (file == null) return;

        Long constId = constituencyFilter.getValue() != null ? constituencyFilter.getValue().getConstituencyId() : null;
        ReportType rt = "APPROVED".equals(verifFilter.getValue()) ? ReportType.APPROVED_VOTERS
                : "PENDING".equals(verifFilter.getValue()) ? ReportType.PENDING_REGISTRATIONS
                : ReportType.VOTER_REGISTRATION;

        AsyncTaskRunner.run(
                () -> {
                    ReportData data = ctx.getReportService().generateReport(rt, searchField.getText(), null, constId, null, null);
                    return pdf ? ctx.getReportService().exportPdf(data, file.toPath()) : ctx.getReportService().exportCsv(data, file.toPath());
                },
                saved -> AlertHelper.showSuccess("Export Complete", "Report saved to:\n" + saved.toAbsolutePath()),
                null,
                loadingToggle,
                "export voter list"
        );
    }
}
