package com.civicflow.controller;

import com.civicflow.app.ApplicationContext;
import com.civicflow.exception.GlobalExceptionHandler;
import com.civicflow.model.AccountStatus;
import com.civicflow.model.AppSetting;
import com.civicflow.model.RoleType;
import com.civicflow.model.User;
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

import java.util.function.Consumer;

/**
 * Super Admin Settings & Administrative Account Management screen.
 */
public class SettingsView {

    private final ApplicationContext ctx;
    private final Consumer<Boolean> loadingToggle;

    private final TableView<User> adminTable = new TableView<>();
    private final TableView<AppSetting> settingsTable = new TableView<>();

    public SettingsView(ApplicationContext ctx, Consumer<Boolean> loadingToggle) {
        this.ctx = ctx;
        this.loadingToggle = loadingToggle;
    }

    public Node build() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab adminTab = new Tab("Administrator Accounts", buildAdminAccountsPane());
        Tab settingsTab = new Tab("Security & Application Settings", buildSettingsPane());
        tabs.getTabs().addAll(adminTab, settingsTab);

        loadAdmins();
        loadSettings();
        return tabs;
    }

    private Node buildAdminAccountsPane() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(16, 0, 0, 0));

        Button createBtn = new Button("+ Create Admin Account");
        createBtn.getStyleClass().add("btn-primary");
        createBtn.setOnAction(e -> openCreateAdminDialog());

        Button toggleRoleBtn = new Button("Switch Admin Role");
        toggleRoleBtn.getStyleClass().add("btn-secondary");
        toggleRoleBtn.setOnAction(e -> withSelectedAdmin(u -> {
            RoleType target = u.getRoleType() == RoleType.SUPER_ADMIN ? RoleType.ELECTION_ADMIN : RoleType.SUPER_ADMIN;
            if (!AlertHelper.confirm("Change Role", "Change role for " + u.getUsername() + "?",
                    "New role will be: " + target.getDisplayName())) return;
            AsyncTaskRunner.run(
                    () -> { ctx.getUserManagementService().updateAdminRole(u.getUserId(), target); return null; },
                    ignored -> { AlertHelper.showSuccess("Updated", "Role updated."); loadAdmins(); },
                    null, loadingToggle, "update admin role");
        }));

        Button toggleStatusBtn = new Button("Suspend / Activate");
        toggleStatusBtn.getStyleClass().add("btn-warning");
        toggleStatusBtn.setOnAction(e -> withSelectedAdmin(u -> {
            AccountStatus next = u.getAccountStatus() == AccountStatus.ACTIVE ? AccountStatus.SUSPENDED : AccountStatus.ACTIVE;
            if (!AlertHelper.confirm("Account Status", "Set " + u.getUsername() + " to " + next + "?", "Confirm status update.")) return;
            AsyncTaskRunner.run(
                    () -> { ctx.getUserManagementService().updateAccountStatus(u.getUserId(), next); return null; },
                    ignored -> { AlertHelper.showSuccess("Updated", "Account status updated to " + next + "."); loadAdmins(); },
                    null, loadingToggle, "update admin status");
        }));

        Button resetPassBtn = new Button("Reset Password");
        resetPassBtn.getStyleClass().add("btn-danger");
        resetPassBtn.setOnAction(e -> withSelectedAdmin(this::openResetPasswordDialog));

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox bar = new HBox(10, toggleRoleBtn, toggleStatusBtn, resetPassBtn, sp, createBtn);
        bar.setAlignment(Pos.CENTER_LEFT);

        adminTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        adminTable.setPrefHeight(420);

        TableColumn<User, String> cUser = new TableColumn<>("Username");
        cUser.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUsername()));
        TableColumn<User, String> cName = new TableColumn<>("Full Name");
        cName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFullName()));
        TableColumn<User, String> cEmail = new TableColumn<>("Email");
        cEmail.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEmail()));
        TableColumn<User, String> cRole = new TableColumn<>("Role");
        cRole.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRoleType().name()));
        cRole.setCellFactory(col -> badgeCell());
        TableColumn<User, String> cStatus = new TableColumn<>("Status");
        cStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAccountStatus().name()));
        cStatus.setCellFactory(col -> badgeCell());
        TableColumn<User, String> cFails = new TableColumn<>("Failed Logins");
        cFails.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getFailedLoginAttempts())));

        adminTable.getColumns().addAll(cUser, cName, cEmail, cRole, cStatus, cFails);

        VBox card = new VBox(12, bar, adminTable);
        card.getStyleClass().add("card");
        box.getChildren().add(card);
        return box;
    }

    private Node buildSettingsPane() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(16, 0, 0, 0));

        Button editBtn = new Button("Edit Selected Setting");
        editBtn.getStyleClass().add("btn-primary");
        editBtn.setOnAction(e -> {
            AppSetting sel = settingsTable.getSelectionModel().getSelectedItem();
            if (sel == null) { AlertHelper.showWarning("No Selection", "Select a setting row first."); return; }
            openEditSettingDialog(sel);
        });

        HBox bar = new HBox(10, new Label("Configurable Runtime & Security Policies (Persisted in MySQL)"), new Region(), editBtn);
        HBox.setHgrow(bar.getChildren().get(1), Priority.ALWAYS);
        bar.setAlignment(Pos.CENTER_LEFT);

        settingsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        settingsTable.setPrefHeight(400);

        TableColumn<AppSetting, String> cKey = new TableColumn<>("Setting Key");
        cKey.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSettingKey()));
        TableColumn<AppSetting, String> cVal = new TableColumn<>("Current Value");
        cVal.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSettingValue()));
        TableColumn<AppSetting, String> cDesc = new TableColumn<>("Description");
        cDesc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));
        settingsTable.getColumns().addAll(cKey, cVal, cDesc);

        VBox card = new VBox(12, bar, settingsTable);
        card.getStyleClass().add("card");
        box.getChildren().add(card);
        return box;
    }

    private TableCell<User, String> badgeCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : UiComponents.createStatusBadge(item));
            }
        };
    }

    private void loadAdmins() {
        AsyncTaskRunner.run(
                () -> ctx.getUserManagementService().getAllAdminAccounts(),
                list -> adminTable.setItems(FXCollections.observableArrayList(list)),
                null, loadingToggle, "load admin accounts"
        );
    }

    private void loadSettings() {
        AsyncTaskRunner.run(
                () -> ctx.getSettingsService().getAllSettings(),
                list -> settingsTable.setItems(FXCollections.observableArrayList(list)),
                null, loadingToggle, "load settings"
        );
    }

    private void withSelectedAdmin(Consumer<User> fn) {
        User sel = adminTable.getSelectionModel().getSelectedItem();
        if (sel == null) { AlertHelper.showWarning("No Selection", "Select an administrator row first."); return; }
        fn.accept(sel);
    }

    private void openCreateAdminDialog() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Create Administrator Account");
        ButtonType createType = new ButtonType("Create Account", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField username = new TextField();
        TextField fullName = new TextField();
        TextField email = new TextField();
        ComboBox<RoleType> roleBox = new ComboBox<>(FXCollections.observableArrayList(RoleType.ELECTION_ADMIN, RoleType.SUPER_ADMIN));
        roleBox.setValue(RoleType.ELECTION_ADMIN);
        PasswordField pass = new PasswordField();
        pass.setPromptText("Min 8 chars, upper/lower/digit/symbol");
        Label errLabel = new Label();
        errLabel.setStyle("-fx-text-fill: #DC4C4C; -fx-font-weight: bold;");
        errLabel.setWrapText(true);

        grid.addRow(0, new Label("Username*:"), username);
        grid.addRow(1, new Label("Full Name*:"), fullName);
        grid.addRow(2, new Label("Email*:"), email);
        grid.addRow(3, new Label("Role*:"), roleBox);
        grid.addRow(4, new Label("Initial Password*:"), pass);
        grid.add(errLabel, 0, 5, 2, 1);
        dialog.getDialogPane().setContent(grid);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(createType);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            try {
                ctx.getUserManagementService().createAdminAccount(
                        username.getText(), fullName.getText(), email.getText(), roleBox.getValue(), pass.getText());
            } catch (Exception ex) {
                errLabel.setText(GlobalExceptionHandler.handleAndGetUserMessage(ex, "create admin account"));
                evt.consume();
            }
        });

        dialog.setResultConverter(b -> b == createType);
        if (dialog.showAndWait().orElse(false)) {
            AlertHelper.showSuccess("Created", "Administrator account created (password change required on first login).");
            loadAdmins();
        }
    }

    private void openResetPasswordDialog(User target) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Admin Password Reset");
        dlg.setHeaderText("Reset password for " + target.getUsername());
        dlg.setContentText("Temporary Strong Password:");
        dlg.showAndWait().ifPresent(newPass -> AsyncTaskRunner.run(
                () -> { ctx.getAuthService().adminResetPassword(target.getUserId(), newPass); return null; },
                ignored -> AlertHelper.showSuccess("Password Reset", "Password reset successfully. User must change password on next login."),
                null, loadingToggle, "reset user password"
        ));
    }

    private void openEditSettingDialog(AppSetting setting) {
        TextInputDialog dlg = new TextInputDialog(setting.getSettingValue());
        dlg.setTitle("Edit Setting");
        dlg.setHeaderText(setting.getSettingKey() + "\n" + (setting.getDescription() != null ? setting.getDescription() : ""));
        dlg.setContentText("New Value:");
        dlg.showAndWait().ifPresent(val -> AsyncTaskRunner.run(
                () -> { ctx.getSettingsService().updateSetting(setting.getSettingKey(), val, setting.getDescription()); return null; },
                ignored -> { AlertHelper.showSuccess("Saved", "Setting updated."); loadSettings(); },
                null, loadingToggle, "update setting"
        ));
    }
}
