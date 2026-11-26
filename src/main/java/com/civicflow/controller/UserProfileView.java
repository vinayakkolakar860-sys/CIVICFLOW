package com.civicflow.controller;

import com.civicflow.app.ApplicationContext;
import com.civicflow.model.User;
import com.civicflow.model.Voter;
import com.civicflow.security.UserSession;
import com.civicflow.ui.AlertHelper;
import com.civicflow.ui.AsyncTaskRunner;
import com.civicflow.ui.UiComponents;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.function.Consumer;

/**
 * User Profile & Password Management screen available to all authenticated roles.
 */
public class UserProfileView {

    private final ApplicationContext ctx;
    private final Consumer<Boolean> loadingToggle;

    public UserProfileView(ApplicationContext ctx, Consumer<Boolean> loadingToggle) {
        this.ctx = ctx;
        this.loadingToggle = loadingToggle;
    }

    public Node build() {
        VBox root = new VBox(18);
        UserSession session = ctx.getSessionManager().getCurrentSession().orElseThrow();
        User user = session.getUser();

        // Account Info & Update Card
        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(14);
        infoGrid.setVgap(10);

        TextField usernameField = new TextField(user.getUsername());
        usernameField.setDisable(true);
        TextField fullNameField = new TextField(user.getFullName());
        fullNameField.setPrefWidth(300);
        TextField emailField = new TextField(user.getEmail());
        emailField.setPrefWidth(300);

        Button saveProfileBtn = new Button("Save Profile Changes");
        saveProfileBtn.getStyleClass().add("btn-primary");
        saveProfileBtn.setOnAction(e -> AsyncTaskRunner.run(
                () -> { ctx.getUserManagementService().updateOwnProfile(fullNameField.getText(), emailField.getText()); return null; },
                ignored -> AlertHelper.showSuccess("Profile Updated", "Your profile information has been saved."),
                null, loadingToggle, "update profile"
        ));

        infoGrid.addRow(0, new Label("Username:"), usernameField, new Label("Role:"), UiComponents.createStatusBadge(user.getRoleType().name()));
        infoGrid.addRow(1, new Label("Full Name:"), fullNameField, new Label("Status:"), UiComponents.createStatusBadge(user.getAccountStatus().name()));
        infoGrid.addRow(2, new Label("Email:"), emailField);
        infoGrid.add(saveProfileBtn, 1, 3);

        VBox profileCard = UiComponents.wrapInCard("Account Details", infoGrid);

        // Voter details card if applicable
        if (session.getVoterProfile() != null) {
            Voter v = session.getVoterProfile();
            Label vDetails = new Label(String.format(
                    "Voter ID: %s\nConstituency: %s (%s)\nVerification Status: %s\nRegistered Address: %s, %s, %s\nPhone: %s",
                    v.getVoterCode(), v.getConstituencyName(), v.getConstituencyCode(),
                    v.getVerificationStatus(), v.getAddress(), v.getCity(), v.getState(), v.getPhoneNumber()));
            vDetails.setWrapText(true);
            root.getChildren().addAll(profileCard, UiComponents.wrapInCard("Simulated Voter Registration Profile", vDetails));
        } else {
            root.getChildren().add(profileCard);
        }

        // Change Password Card
        GridPane passGrid = new GridPane();
        passGrid.setHgap(14);
        passGrid.setVgap(12);
        passGrid.setPadding(new Insets(4, 0, 0, 0));

        PasswordField currentPass = new PasswordField();
        currentPass.setPromptText("Enter current password");
        currentPass.setPrefWidth(300);
        TextField currentPassText = new TextField();
        currentPassText.setPromptText("Enter current password");
        currentPassText.setPrefWidth(300);
        currentPassText.setVisible(false);
        currentPassText.setManaged(false);
        currentPassText.textProperty().bindBidirectional(currentPass.textProperty());

        PasswordField newPass = new PasswordField();
        newPass.setPromptText("Must be exactly 6 characters");
        newPass.setPrefWidth(300);
        TextField newPassText = new TextField();
        newPassText.setPromptText("Must be exactly 6 characters");
        newPassText.setPrefWidth(300);
        newPassText.setVisible(false);
        newPassText.setManaged(false);
        newPassText.textProperty().bindBidirectional(newPass.textProperty());

        PasswordField confirmPass = new PasswordField();
        confirmPass.setPromptText("Confirm new 6-character password");
        confirmPass.setPrefWidth(300);
        TextField confirmPassText = new TextField();
        confirmPassText.setPromptText("Confirm new 6-character password");
        confirmPassText.setPrefWidth(300);
        confirmPassText.setVisible(false);
        confirmPassText.setManaged(false);
        confirmPassText.textProperty().bindBidirectional(confirmPass.textProperty());

        CheckBox showPassCheck = new CheckBox("Show Passwords");
        showPassCheck.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");
        showPassCheck.setOnAction(e -> {
            boolean show = showPassCheck.isSelected();
            currentPass.setVisible(!show);
            currentPass.setManaged(!show);
            currentPassText.setVisible(show);
            currentPassText.setManaged(show);
            newPass.setVisible(!show);
            newPass.setManaged(!show);
            newPassText.setVisible(show);
            newPassText.setManaged(show);
            confirmPass.setVisible(!show);
            confirmPass.setManaged(!show);
            confirmPassText.setVisible(show);
            confirmPassText.setManaged(show);
        });

        Label errLabel = new Label();
        errLabel.getStyleClass().add("error-label");

        Button changePassBtn = new Button("Change Password");
        changePassBtn.getStyleClass().add("btn-primary");
        changePassBtn.setOnAction(e -> {
            errLabel.setText("");
            String cp = currentPass.getText();
            String np = newPass.getText();
            String cnp = confirmPass.getText();

            if (cp == null || cp.isBlank() || np == null || np.isBlank() || cnp == null || cnp.isBlank()) {
                errLabel.setText("All password fields are required.");
                return;
            }
            if (np.length() != 6) {
                errLabel.setText("Password must contain exactly 6 characters.");
                return;
            }
            if (!np.equals(cnp)) {
                errLabel.setText("New password and confirmation do not match.");
                return;
            }
            if (cp.equals(np)) {
                errLabel.setText("New password must differ from your current password.");
                return;
            }

            AsyncTaskRunner.run(
                    () -> { ctx.getAuthService().changeOwnPassword(cp, np, cnp); return null; },
                    ignored -> {
                        currentPass.clear();
                        newPass.clear();
                        confirmPass.clear();
                        AlertHelper.showSuccess("Password Changed", "Your password has been updated and securely re-hashed with BCrypt.");
                    },
                    errLabel::setText,
                    loadingToggle,
                    "change password"
            );
        });

        passGrid.addRow(0, new Label("Current Password:"), new StackPane(currentPass, currentPassText));
        passGrid.addRow(1, new Label("New Password:"), new StackPane(newPass, newPassText));
        passGrid.addRow(2, new Label("Confirm New Password:"), new StackPane(confirmPass, confirmPassText));
        passGrid.add(showPassCheck, 1, 3);
        passGrid.add(errLabel, 1, 4);
        passGrid.add(changePassBtn, 1, 5);

        root.getChildren().add(UiComponents.wrapInCard("Change Password", passGrid));
        return root;
    }
}
