package com.civicflow.controller;

import com.civicflow.app.ApplicationContext;
import com.civicflow.exception.GlobalExceptionHandler;
import com.civicflow.model.Party;
import com.civicflow.model.PartyStatus;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Fictional Party Management screen with CRUD, search, and validated logo upload.
 */
public class PartyManagementView {

    private final ApplicationContext ctx;
    private final Consumer<Boolean> loadingToggle;
    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final TableView<Party> table = new TableView<>();

    public PartyManagementView(ApplicationContext ctx, Consumer<Boolean> loadingToggle) {
        this.ctx = ctx;
        this.loadingToggle = loadingToggle;
    }

    public Node build() {
        VBox root = new VBox(16);

        searchField.setPromptText("Search parties by code or name...");
        searchField.setPrefWidth(260);
        searchField.setOnAction(e -> loadParties());

        statusFilter.getItems().setAll("ALL", "ACTIVE", "INACTIVE");
        statusFilter.setValue("ALL");
        statusFilter.setOnAction(e -> loadParties());

        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().add("btn-secondary");
        searchBtn.setOnAction(e -> loadParties());

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button addBtn = new Button("+ Add Party");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> openPartyDialog(null));

        Button editBtn = new Button("Edit Selected");
        editBtn.getStyleClass().add("btn-secondary");
        editBtn.setOnAction(e -> {
            Party sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) { AlertHelper.showWarning("No Selection", "Select a party first."); return; }
            openPartyDialog(sel);
        });

        Button delBtn = new Button("Delete");
        delBtn.getStyleClass().add("btn-danger");
        delBtn.setOnAction(e -> {
            Party sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) { AlertHelper.showWarning("No Selection", "Select a party first."); return; }
            if (!AlertHelper.confirm("Delete Party", "Delete " + sel.getPartyName() + "?",
                    "Deletion is only permitted when no candidates are associated with this party.")) return;
            AsyncTaskRunner.run(
                    () -> { ctx.getPartyService().delete(sel.getPartyId()); return null; },
                    ignored -> { AlertHelper.showSuccess("Deleted", "Party deleted."); loadParties(); },
                    null, loadingToggle, "delete party");
        });

        HBox toolbar = new HBox(10, searchField, statusFilter, searchBtn, sp, editBtn, delBtn, addBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(500);
        table.setPlaceholder(UiComponents.createEmptyState("No Parties", "Manage demonstration parties for your simulated elections."));

        TableColumn<Party, String> cCode = new TableColumn<>("Code");
        cCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPartyCode()));
        TableColumn<Party, String> cName = new TableColumn<>("Party Name");
        cName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPartyName()));
        TableColumn<Party, String> cDesc = new TableColumn<>("Platform Summary");
        cDesc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));
        TableColumn<Party, String> cCount = new TableColumn<>("Candidates");
        cCount.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getCandidateCount())));
        TableColumn<Party, String> cStatus = new TableColumn<>("Status");
        cStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus().name()));
        cStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : UiComponents.createStatusBadge(item));
            }
        });
        table.getColumns().addAll(cCode, cName, cDesc, cCount, cStatus);

        VBox card = new VBox(12, toolbar, table);
        card.getStyleClass().add("card");

        root.getChildren().addAll(
                UiComponents.createDemoBanner("FICTIONAL PARTIES ONLY — Never use real-world political party names, emblems, or branding."),
                card
        );
        loadParties();
        return root;
    }

    private void loadParties() {
        PartyStatus st = "ALL".equals(statusFilter.getValue()) ? null : PartyStatus.valueOf(statusFilter.getValue());
        AsyncTaskRunner.run(
                () -> ctx.getPartyService().getAll(searchField.getText(), st),
                list -> table.setItems(FXCollections.observableArrayList(list)),
                null, loadingToggle, "load parties"
        );
    }

    private void openPartyDialog(Party existing) {
        boolean isEdit = existing != null;
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Fictional Party" : "Add Fictional Party");
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField code = new TextField(isEdit ? existing.getPartyCode() : "");
        code.setPromptText("e.g., CFA");
        TextField name = new TextField(isEdit ? existing.getPartyName() : "");
        name.setPromptText("e.g., Civic Future Alliance");
        ComboBox<PartyStatus> statusBox = new ComboBox<>(FXCollections.observableArrayList(PartyStatus.values()));
        statusBox.setValue(isEdit ? existing.getStatus() : PartyStatus.ACTIVE);
        TextArea desc = new TextArea(isEdit && existing.getDescription() != null ? existing.getDescription() : "");
        desc.setPrefRowCount(3);
        desc.setWrapText(true);

        AtomicReference<Path> logoPath = new AtomicReference<>();
        Label logoLabel = new Label(isEdit && existing.getLogoPath() != null ? "Logo attached" : "No logo selected (optional)");
        Button logoBtn = new Button("Upload Logo (PNG/JPG)");
        logoBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
        logoBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
            File f = fc.showOpenDialog(dialog.getOwner());
            if (f != null) {
                logoPath.set(f.toPath());
                logoLabel.setText(f.getName());
            }
        });

        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill: #DC4C4C; -fx-font-weight: bold;");
        errLabel.setWrapText(true);

        grid.addRow(0, new Label("Party Code*:"), code);
        grid.addRow(1, new Label("Party Name*:"), name);
        grid.addRow(2, new Label("Status:"), statusBox);
        grid.addRow(3, new Label("Description:"), desc);
        grid.addRow(4, new Label("Party Logo:"), new HBox(10, logoBtn, logoLabel));
        grid.add(errLabel, 0, 5, 2, 1);
        dialog.getDialogPane().setContent(grid);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            try {
                Party p = isEdit ? existing : new Party();
                p.setPartyCode(code.getText());
                p.setPartyName(name.getText());
                p.setStatus(statusBox.getValue());
                p.setDescription(desc.getText());
                if (isEdit) {
                    ctx.getPartyService().update(p, logoPath.get());
                } else {
                    ctx.getPartyService().create(p, logoPath.get());
                }
            } catch (Exception ex) {
                errLabel.setText(GlobalExceptionHandler.handleAndGetUserMessage(ex, "save party"));
                evt.consume();
            }
        });

        dialog.setResultConverter(b -> b == saveType);
        if (dialog.showAndWait().orElse(false)) {
            AlertHelper.showSuccess("Saved", isEdit ? "Fictional party updated." : "Fictional party added.");
            loadParties();
        }
    }
}
