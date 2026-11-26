package com.civicflow.controller;

import com.civicflow.app.ApplicationContext;
import com.civicflow.exception.GlobalExceptionHandler;
import com.civicflow.model.Constituency;
import com.civicflow.model.ConstituencyStatus;
import com.civicflow.model.Election;
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

import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Constituency Management screen with unique code enforcement, deletion protection, and election history viewer.
 */
public class ConstituencyManagementView {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ApplicationContext ctx;
    private final Consumer<Boolean> loadingToggle;
    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final TableView<Constituency> table = new TableView<>();

    public ConstituencyManagementView(ApplicationContext ctx, Consumer<Boolean> loadingToggle) {
        this.ctx = ctx;
        this.loadingToggle = loadingToggle;
    }

    public Node build() {
        VBox root = new VBox(16);

        searchField.setPromptText("Search by code, name, city, state...");
        searchField.setPrefWidth(260);
        searchField.setOnAction(e -> loadConstituencies());

        statusFilter.getItems().setAll("ALL", "ACTIVE", "INACTIVE");
        statusFilter.setValue("ALL");
        statusFilter.setOnAction(e -> loadConstituencies());

        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().add("btn-secondary");
        searchBtn.setOnAction(e -> loadConstituencies());

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button historyBtn = new Button("Election History");
        historyBtn.getStyleClass().add("btn-secondary");
        historyBtn.setOnAction(e -> withSelected(this::showElectionHistoryDialog));

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("btn-secondary");
        editBtn.setOnAction(e -> withSelected(this::openConstituencyDialog));

        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setOnAction(e -> withSelected(c -> {
            if (!AlertHelper.confirm("Delete Constituency", "Delete " + c.getConstituencyName() + "?",
                    "Constituencies with assigned voters, candidates, or elections cannot be deleted.")) return;
            AsyncTaskRunner.run(
                    () -> { ctx.getConstituencyService().delete(c.getConstituencyId()); return null; },
                    ignored -> { AlertHelper.showSuccess("Deleted", "Constituency removed."); loadConstituencies(); },
                    null, loadingToggle, "delete constituency");
        }));

        Button addBtn = new Button("+ Add Constituency");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> openConstituencyDialog(null));

        HBox toolbar = new HBox(10, searchField, statusFilter, searchBtn, sp, historyBtn, editBtn, deleteBtn, addBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(500);
        table.setPlaceholder(UiComponents.createEmptyState("No Constituencies", "Add fictional constituencies to organize voters and candidates."));

        TableColumn<Constituency, String> cCode = new TableColumn<>("Code");
        cCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getConstituencyCode()));
        TableColumn<Constituency, String> cName = new TableColumn<>("Constituency Name");
        cName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getConstituencyName()));
        TableColumn<Constituency, String> cCity = new TableColumn<>("City");
        cCity.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCity()));
        TableColumn<Constituency, String> cState = new TableColumn<>("State / Region");
        cState.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getState()));
        TableColumn<Constituency, String> cVoters = new TableColumn<>("Voters");
        cVoters.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getVoterCount())));
        TableColumn<Constituency, String> cCands = new TableColumn<>("Candidates");
        cCands.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getCandidateCount())));
        TableColumn<Constituency, String> cStatus = new TableColumn<>("Status");
        cStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus().name()));
        cStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : UiComponents.createStatusBadge(item));
            }
        });
        table.getColumns().addAll(cCode, cName, cCity, cState, cVoters, cCands, cStatus);

        VBox card = new VBox(12, toolbar, table);
        card.getStyleClass().add("card");
        root.getChildren().add(card);
        loadConstituencies();
        return root;
    }

    private void loadConstituencies() {
        ConstituencyStatus st = "ALL".equals(statusFilter.getValue()) ? null : ConstituencyStatus.valueOf(statusFilter.getValue());
        AsyncTaskRunner.run(
                () -> ctx.getConstituencyService().getAll(searchField.getText(), st),
                list -> table.setItems(FXCollections.observableArrayList(list)),
                null, loadingToggle, "load constituencies"
        );
    }

    private void withSelected(Consumer<Constituency> fn) {
        Constituency sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { AlertHelper.showWarning("No Selection", "Select a constituency row first."); return; }
        fn.accept(sel);
    }

    private void openConstituencyDialog(Constituency existing) {
        boolean isEdit = existing != null;
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Constituency" : "Add Fictional Constituency");
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField code = new TextField(isEdit ? existing.getConstituencyCode() : "");
        code.setPromptText("e.g., CST-NORTH-01");
        TextField name = new TextField(isEdit ? existing.getConstituencyName() : "");
        TextField city = new TextField(isEdit ? existing.getCity() : "");
        TextField state = new TextField(isEdit ? existing.getState() : "");
        ComboBox<ConstituencyStatus> status = new ComboBox<>(FXCollections.observableArrayList(ConstituencyStatus.values()));
        status.setValue(isEdit ? existing.getStatus() : ConstituencyStatus.ACTIVE);
        TextArea desc = new TextArea(isEdit && existing.getDescription() != null ? existing.getDescription() : "");
        desc.setPrefRowCount(3);

        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill: #DC4C4C; -fx-font-weight: bold;");
        errLabel.setWrapText(true);

        grid.addRow(0, new Label("Code*:"), code);
        grid.addRow(1, new Label("Name*:"), name);
        grid.addRow(2, new Label("City*:"), city);
        grid.addRow(3, new Label("State*:"), state);
        grid.addRow(4, new Label("Status:"), status);
        grid.addRow(5, new Label("Description:"), desc);
        grid.add(errLabel, 0, 6, 2, 1);
        dialog.getDialogPane().setContent(grid);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            try {
                Constituency c = isEdit ? existing : new Constituency();
                c.setConstituencyCode(code.getText());
                c.setConstituencyName(name.getText());
                c.setCity(city.getText());
                c.setState(state.getText());
                c.setStatus(status.getValue());
                c.setDescription(desc.getText());
                if (isEdit) ctx.getConstituencyService().update(c);
                else ctx.getConstituencyService().create(c);
            } catch (Exception ex) {
                errLabel.setText(GlobalExceptionHandler.handleAndGetUserMessage(ex, "save constituency"));
                evt.consume();
            }
        });

        dialog.setResultConverter(b -> b == saveType);
        if (dialog.showAndWait().orElse(false)) {
            AlertHelper.showSuccess("Saved", isEdit ? "Constituency updated." : "Constituency created.");
            loadConstituencies();
        }
    }

    private void showElectionHistoryDialog(Constituency c) {
        AsyncTaskRunner.run(
                () -> ctx.getConstituencyService().getElectionHistory(c.getConstituencyId()),
                elections -> {
                    Dialog<Void> dialog = new Dialog<>();
                    dialog.setTitle("Election History — " + c.getConstituencyName());
                    dialog.setHeaderText(c.getConstituencyCode() + " (" + c.getCity() + ", " + c.getState() + ")");
                    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

                    TableView<Election> t = new TableView<>();
                    t.setPrefSize(560, 260);
                    t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
                    t.setPlaceholder(new Label("This constituency has not been assigned to any elections yet."));

                    TableColumn<Election, String> c1 = new TableColumn<>("Code");
                    c1.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getElectionCode()));
                    TableColumn<Election, String> c2 = new TableColumn<>("Election Name");
                    c2.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getElectionName()));
                    TableColumn<Election, String> c3 = new TableColumn<>("Start");
                    c3.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStartDateTime().format(DT_FMT)));
                    TableColumn<Election, String> c4 = new TableColumn<>("Status");
                    c4.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus().name()));
                    t.getColumns().addAll(c1, c2, c3, c4);
                    t.setItems(FXCollections.observableArrayList(elections));

                    dialog.getDialogPane().setContent(t);
                    dialog.showAndWait();
                },
                null, loadingToggle, "load constituency history"
        );
    }
}
