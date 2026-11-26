package com.civicflow.controller;

import com.civicflow.app.ApplicationContext;
import com.civicflow.exception.GlobalExceptionHandler;
import com.civicflow.model.*;
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
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Candidate Management screen with search, filtering, add/edit/delete, approval workflow, and validated photo upload.
 */
public class CandidateManagementView {

    private final ApplicationContext ctx;
    private final Consumer<Boolean> loadingToggle;

    private final TextField searchField = new TextField();
    private final ComboBox<Constituency> constFilter = new ComboBox<>();
    private final ComboBox<Party> partyFilter = new ComboBox<>();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final TableView<Candidate> table = new TableView<>();

    private List<Constituency> constituencies = List.of();
    private List<Party> parties = List.of();

    public CandidateManagementView(ApplicationContext ctx, Consumer<Boolean> loadingToggle) {
        this.ctx = ctx;
        this.loadingToggle = loadingToggle;
    }

    public Node build() {
        VBox root = new VBox(16);

        searchField.setPromptText("Search by code, name, email, party...");
        searchField.setPrefWidth(240);
        searchField.setOnAction(e -> loadCandidates());

        constFilter.setPromptText("All Constituencies");
        constFilter.setPrefWidth(180);
        constFilter.setOnAction(e -> loadCandidates());

        partyFilter.setPromptText("All Parties");
        partyFilter.setPrefWidth(180);
        partyFilter.setOnAction(e -> loadCandidates());

        statusFilter.getItems().setAll("ALL", "PENDING", "APPROVED", "REJECTED", "WITHDRAWN");
        statusFilter.setValue("ALL");
        statusFilter.setOnAction(e -> loadCandidates());

        Button resetBtn = new Button("Reset");
        resetBtn.getStyleClass().add("btn-secondary");
        resetBtn.setOnAction(e -> {
            searchField.clear();
            constFilter.setValue(null);
            partyFilter.setValue(null);
            statusFilter.setValue("ALL");
            loadCandidates();
        });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button addBtn = new Button("+ Add Candidate");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> openCandidateDialog(null));

        HBox toolbar = new HBox(10, searchField, constFilter, partyFilter, statusFilter, resetBtn, sp, addBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button viewBtn = new Button("View Profile");
        viewBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
        viewBtn.setOnAction(e -> withSelected(this::showProfileDialog));

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
        editBtn.setOnAction(e -> withSelected(this::openCandidateDialog));

        Button approveBtn = new Button("Approve");
        approveBtn.getStyleClass().addAll("btn-success", "btn-sm");
        approveBtn.setOnAction(e -> withSelected(c -> runAction(
                "Approve Candidate", "Approve candidate " + c.getFullName() + "?",
                () -> ctx.getCandidateService().approveCandidate(c.getCandidateId()), "Candidate approved.")));

        Button rejectBtn = new Button("Reject");
        rejectBtn.getStyleClass().addAll("btn-warning", "btn-sm");
        rejectBtn.setOnAction(e -> withSelected(c -> runAction(
                "Reject Candidate", "Reject candidate " + c.getFullName() + "?",
                () -> ctx.getCandidateService().rejectCandidate(c.getCandidateId()), "Candidate rejected.")));

        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().addAll("btn-danger", "btn-sm");
        deleteBtn.setOnAction(e -> withSelected(c -> runAction(
                "Delete Candidate", "Permanently delete candidate " + c.getFullName() + "? (Blocked if linked to active/completed elections.)",
                () -> ctx.getCandidateService().deleteCandidate(c.getCandidateId()), "Candidate deleted.")));

        HBox rowActions = new HBox(8, new Label("Selected Candidate:"), viewBtn, editBtn, approveBtn, rejectBtn, deleteBtn);
        rowActions.setAlignment(Pos.CENTER_LEFT);

        configureTable();
        VBox card = new VBox(12, toolbar, rowActions, table);
        card.getStyleClass().add("card");
        VBox.setVgrow(table, Priority.ALWAYS);

        root.getChildren().addAll(card);
        loadMetadataAndData();
        return root;
    }

    private void configureTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(500);
        table.setPlaceholder(UiComponents.createEmptyState("No Candidates Found", "Add fictional candidates to assign them to elections."));

        TableColumn<Candidate, String> cCode = new TableColumn<>("Code");
        cCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCandidateCode()));
        TableColumn<Candidate, String> cName = new TableColumn<>("Candidate Name");
        cName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFullName()));
        TableColumn<Candidate, String> cAge = new TableColumn<>("Age");
        cAge.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getAge())));
        TableColumn<Candidate, String> cParty = new TableColumn<>("Party");
        cParty.setCellValueFactory(d -> {
            Candidate c = d.getValue();
            String pDisplay = c.getPartyCode() != null && c.getPartyCode().equalsIgnoreCase(c.getPartyName())
                    ? c.getPartyName()
                    : c.getPartyName() + " (" + c.getPartyCode() + ")";
            return new SimpleStringProperty(pDisplay);
        });
        TableColumn<Candidate, String> cConst = new TableColumn<>("Constituency");
        cConst.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getConstituencyName()));
        TableColumn<Candidate, String> cEmail = new TableColumn<>("Email");
        cEmail.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEmail()));
        TableColumn<Candidate, String> cStatus = new TableColumn<>("Status");
        cStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus().name()));
        cStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : UiComponents.createStatusBadge(item));
            }
        });

        table.getColumns().addAll(cCode, cName, cAge, cParty, cConst, cEmail, cStatus);
    }

    private record Meta(List<Constituency> constituencies, List<Party> parties) {}

    private void loadMetadataAndData() {
        AsyncTaskRunner.run(
                () -> new Meta(
                        ctx.getConstituencyService().getAll(null, ConstituencyStatus.ACTIVE),
                        ctx.getPartyService().getAll(null, PartyStatus.ACTIVE)),
                meta -> {
                    constituencies = meta.constituencies();
                    parties = meta.parties();
                    constFilter.getItems().setAll(constituencies);
                    partyFilter.getItems().setAll(parties);
                    loadCandidates();
                },
                null,
                loadingToggle,
                "load candidate metadata"
        );
    }

    private void loadCandidates() {
        Long cId = constFilter.getValue() != null ? constFilter.getValue().getConstituencyId() : null;
        Long pId = partyFilter.getValue() != null ? partyFilter.getValue().getPartyId() : null;
        CandidateStatus st = "ALL".equals(statusFilter.getValue()) ? null : CandidateStatus.valueOf(statusFilter.getValue());

        AsyncTaskRunner.run(
                () -> ctx.getCandidateService().search(searchField.getText(), cId, pId, st),
                list -> table.setItems(FXCollections.observableArrayList(list)),
                null,
                loadingToggle,
                "load candidates"
        );
    }

    private void withSelected(Consumer<Candidate> fn) {
        Candidate sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AlertHelper.showWarning("No Selection", "Select a candidate from the table first.");
            return;
        }
        fn.accept(sel);
    }

    private void runAction(String title, String prompt, Runnable work, String success) {
        if (!AlertHelper.confirm(title, title, prompt)) return;
        AsyncTaskRunner.run(
                () -> { work.run(); return null; },
                ignored -> { AlertHelper.showSuccess("Done", success); loadCandidates(); },
                null,
                loadingToggle,
                title
        );
    }

    private void openCandidateDialog(Candidate existing) {
        boolean isEdit = existing != null;
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Candidate" : "Add Fictional Candidate");
        ButtonType saveType = new ButtonType(isEdit ? "Save Changes" : "Register Candidate", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField name = new TextField(isEdit ? existing.getFullName() : "");
        TextField age = new TextField(isEdit ? String.valueOf(existing.getAge()) : "34");
        TextField email = new TextField(isEdit ? existing.getEmail() : "");
        TextField phone = new TextField(isEdit ? existing.getContactNumber() : "");
        ComboBox<Party> partyBox = new ComboBox<>(FXCollections.observableArrayList(parties));
        partyBox.setPrefWidth(240);
        ComboBox<Constituency> constBox = new ComboBox<>(FXCollections.observableArrayList(constituencies));
        constBox.setPrefWidth(240);
        TextArea bio = new TextArea(isEdit && existing.getProfileDescription() != null ? existing.getProfileDescription() : "");
        bio.setPrefRowCount(3);
        bio.setWrapText(true);

        if (isEdit) {
            parties.stream().filter(p -> p.getPartyId() == existing.getPartyId()).findFirst().ifPresent(partyBox::setValue);
            constituencies.stream().filter(c -> c.getConstituencyId() == existing.getConstituencyId()).findFirst().ifPresent(constBox::setValue);
        } else {
            if (!parties.isEmpty()) partyBox.setValue(parties.get(0));
            if (!constituencies.isEmpty()) constBox.setValue(constituencies.get(0));
        }

        AtomicReference<Path> selectedPhoto = new AtomicReference<>();
        Label photoLabel = new Label(isEdit && existing.getPhotoPath() != null ? "Photo configured" : "No photo selected (optional)");
        Button choosePhotoBtn = new Button("Choose Photo (PNG/JPG)");
        choosePhotoBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
        choosePhotoBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
            File f = fc.showOpenDialog(dialog.getOwner());
            if (f != null) {
                selectedPhoto.set(f.toPath());
                photoLabel.setText(f.getName());
            }
        });

        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill: #DC4C4C; -fx-font-weight: bold;");
        errLabel.setWrapText(true);

        int r = 0;
        grid.addRow(r++, new Label("Full Name*:"), name, new Label("Age*:"), age);
        grid.addRow(r++, new Label("Email*:"), email, new Label("Contact*:"), phone);
        grid.addRow(r++, new Label("Fictional Party*:"), partyBox, new Label("Constituency*:"), constBox);
        grid.add(new Label("Profile Bio:"), 0, r);
        grid.add(bio, 1, r++, 3, 1);
        grid.add(new Label("Profile Photo:"), 0, r);
        grid.add(new HBox(10, choosePhotoBtn, photoLabel), 1, r++, 3, 1);
        grid.add(errLabel, 0, r, 4, 1);
        dialog.getDialogPane().setContent(grid);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            try {
                Candidate c = isEdit ? existing : new Candidate();
                c.setFullName(name.getText());
                try {
                    c.setAge(Integer.parseInt(age.getText().trim()));
                } catch (NumberFormatException ex) {
                    throw new com.civicflow.exception.ValidationException("Age must be a valid whole number.");
                }
                c.setEmail(email.getText());
                c.setContactNumber(phone.getText());
                c.setProfileDescription(bio.getText());
                if (partyBox.getValue() == null) throw new com.civicflow.exception.ValidationException("Select a fictional party.");
                if (constBox.getValue() == null) throw new com.civicflow.exception.ValidationException("Select a constituency.");
                c.setPartyId(partyBox.getValue().getPartyId());
                c.setConstituencyId(constBox.getValue().getConstituencyId());

                if (isEdit) {
                    ctx.getCandidateService().updateCandidate(c, selectedPhoto.get());
                } else {
                    ctx.getCandidateService().registerCandidate(c, selectedPhoto.get());
                }
            } catch (Exception ex) {
                errLabel.setText(GlobalExceptionHandler.handleAndGetUserMessage(ex, "save candidate"));
                evt.consume();
            }
        });

        dialog.setResultConverter(b -> b == saveType);
        if (dialog.showAndWait().orElse(false)) {
            AlertHelper.showSuccess("Saved", isEdit ? "Candidate updated." : "Candidate registered.");
            loadCandidates();
        }
    }

    private void showProfileDialog(Candidate c) {
        AlertHelper.showInfo("Candidate Profile — " + c.getCandidateCode(),
                "Name: " + c.getFullName() + "\n"
                        + "Age: " + c.getAge() + "\n"
                        + "Fictional Party: " + c.getPartyName() + " (" + c.getPartyCode() + ")\n"
                        + "Constituency: " + c.getConstituencyName() + " (" + c.getConstituencyCode() + ")\n"
                        + "Email: " + c.getEmail() + "  |  Contact: " + c.getContactNumber() + "\n"
                        + "Status: " + c.getStatus() + "\n\n"
                        + "Profile Summary:\n" + (c.getProfileDescription() != null ? c.getProfileDescription() : "No description provided."));
    }
}
