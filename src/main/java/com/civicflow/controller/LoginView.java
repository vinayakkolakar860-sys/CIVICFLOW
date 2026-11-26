package com.civicflow.controller;

import com.civicflow.app.ApplicationContext;
import com.civicflow.exception.GlobalExceptionHandler;
import com.civicflow.security.UserSession;
import com.civicflow.ui.AlertHelper;
import com.civicflow.ui.AsyncTaskRunner;
import com.civicflow.ui.CivicFlowLogo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Clean, light-theme CIVICFLOW Login screen with inline validation,
 * show/hide password toggle, remember-me support, and generic auth failure handling.
 */
public class LoginView {

    private static final String PREF_SAVED_USERNAME = "saved_username";

    private final ApplicationContext ctx;
    private final Consumer<UserSession> onLoginSuccess;

    public LoginView(ApplicationContext ctx, Consumer<UserSession> onLoginSuccess) {
        this.ctx = ctx;
        this.onLoginSuccess = onLoginSuccess;
    }

    public Parent build() {
        StackPane root = new StackPane();
        root.getStyleClass().add("login-root");
        root.setPadding(new Insets(24));

        VBox card = new VBox(20);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(420);
        card.setAlignment(Pos.TOP_CENTER);
        StackPane.setAlignment(card, Pos.CENTER);

        // Header Section
        VBox header = new VBox(8);
        header.setAlignment(Pos.CENTER);

        HBox logoContainer = new HBox(CivicFlowLogo.createLogoMark(46));
        logoContainer.setAlignment(Pos.CENTER);

        Label brandName = new Label("CIVICFLOW");
        brandName.getStyleClass().add("login-brand-title");

        Label heading = new Label("Welcome Back");
        heading.getStyleClass().add("login-title");

        Label sub = new Label("Sign in to continue.");
        sub.getStyleClass().add("login-sub");

        header.getChildren().addAll(logoContainer, brandName, heading, sub);

        // Form Fields
        VBox form = new VBox(16);
        form.setAlignment(Pos.TOP_LEFT);

        // Username
        Label userLabel = new Label("Username");
        userLabel.getStyleClass().add("form-label");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter your username");
        Label userError = new Label();
        userError.getStyleClass().add("error-label");
        userError.setVisible(false);
        userError.setManaged(false);

        // Remembered Username recovery
        Preferences prefs = Preferences.userNodeForPackage(LoginView.class);
        String savedUser = prefs.get(PREF_SAVED_USERNAME, "");
        if (!savedUser.isBlank()) {
            usernameField.setText(savedUser);
        }

        VBox userBox = new VBox(5, userLabel, usernameField, userError);

        // Password with Show/Hide toggle
        Label passLabel = new Label("Password");
        passLabel.getStyleClass().add("form-label");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter your password");

        TextField textPasswordField = new TextField();
        textPasswordField.setPromptText("Enter your password");
        textPasswordField.setVisible(false);
        textPasswordField.setManaged(false);

        // Sync values bidirectionally
        textPasswordField.textProperty().bindBidirectional(passwordField.textProperty());

        Button togglePassBtn = new Button("Show");
        togglePassBtn.getStyleClass().add("login-toggle-btn");

        togglePassBtn.setOnAction(e -> {
            if (passwordField.isVisible()) {
                passwordField.setVisible(false);
                passwordField.setManaged(false);
                textPasswordField.setVisible(true);
                textPasswordField.setManaged(true);
                togglePassBtn.setText("Hide");
                textPasswordField.requestFocus();
                textPasswordField.positionCaret(textPasswordField.getText().length());
            } else {
                textPasswordField.setVisible(false);
                textPasswordField.setManaged(false);
                passwordField.setVisible(true);
                passwordField.setManaged(true);
                togglePassBtn.setText("Show");
                passwordField.requestFocus();
                passwordField.positionCaret(passwordField.getText().length());
            }
        });

        StackPane passFieldStack = new StackPane(passwordField, textPasswordField, togglePassBtn);
        StackPane.setAlignment(togglePassBtn, Pos.CENTER_RIGHT);
        StackPane.setMargin(togglePassBtn, new Insets(0, 8, 0, 0));

        Label passError = new Label();
        passError.getStyleClass().add("error-label");
        passError.setVisible(false);
        passError.setManaged(false);

        VBox passBox = new VBox(5, passLabel, passFieldStack, passError);

        // Remember Me checkbox
        CheckBox rememberMeCheck = new CheckBox("Remember me");
        rememberMeCheck.setStyle("-fx-text-fill: #475569; -fx-font-size: 13px;");
        if (!savedUser.isBlank()) {
            rememberMeCheck.setSelected(true);
        }

        // Generic Auth Error Banner
        Label authErrorBanner = new Label();
        authErrorBanner.setWrapText(true);
        authErrorBanner.setStyle("-fx-background-color: #FEF2F2; -fx-border-color: #FCA5A5; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: #991B1B; -fx-padding: 10 14; -fx-font-size: 12.5px; -fx-font-weight: 500;");
        authErrorBanner.setVisible(false);
        authErrorBanner.setManaged(false);

        // Sign In Button & Loading Indicator
        Button loginBtn = new Button("Sign In");
        loginBtn.getStyleClass().add("btn-primary");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setDefaultButton(true);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(20, 20);
        spinner.setVisible(false);
        spinner.setManaged(false);

        HBox actionRow = new HBox(10, loginBtn, spinner);
        actionRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(loginBtn, Priority.ALWAYS);

        // Authentication Submission Handler
        Runnable attemptLogin = () -> {
            clearFieldError(usernameField, userError);
            clearFieldError(passwordField, passError);
            clearFieldError(textPasswordField, passError);
            authErrorBanner.setVisible(false);
            authErrorBanner.setManaged(false);

            String u = usernameField.getText();
            String p = passwordField.getText();
            boolean valid = true;
            if (u == null || u.isBlank()) {
                setFieldError(usernameField, userError, "Please enter your username.");
                valid = false;
            }
            if (p == null || p.isBlank()) {
                setFieldError(passwordField, passError, "Please enter your password.");
                setFieldError(textPasswordField, passError, "Please enter your password.");
                valid = false;
            }
            if (!valid) return;

            loginBtn.setDisable(true);
            final String inputPass = p;
            final String inputUser = u.trim();

            AsyncTaskRunner.run(
                    () -> ctx.getAuthService().login(inputUser, inputPass),
                    session -> {
                        loginBtn.setDisable(false);
                        passwordField.clear();

                        // Persist Remember Me setting
                        if (rememberMeCheck.isSelected()) {
                            prefs.put(PREF_SAVED_USERNAME, inputUser);
                        } else {
                            prefs.remove(PREF_SAVED_USERNAME);
                        }

                        if (session.isMustChangePassword()) {
                            boolean changed = showFirstLoginPasswordChangeDialog(inputPass);
                            if (!changed) {
                                ctx.getAuthService().logout();
                                authErrorBanner.setText("Password change is required on first login for seeded demonstration accounts.");
                                authErrorBanner.setVisible(true);
                                authErrorBanner.setManaged(true);
                                return;
                            }
                            session = ctx.getSessionManager().getCurrentSession().orElse(session);
                        }
                        onLoginSuccess.accept(session);
                    },
                    errMsg -> {
                        loginBtn.setDisable(false);
                        authErrorBanner.setText(errMsg);
                        authErrorBanner.setVisible(true);
                        authErrorBanner.setManaged(true);
                    },
                    loading -> {
                        spinner.setVisible(loading);
                        spinner.setManaged(loading);
                    },
                    "user login"
            );
        };

        loginBtn.setOnAction(e -> attemptLogin.run());
        passwordField.setOnAction(e -> attemptLogin.run());
        textPasswordField.setOnAction(e -> attemptLogin.run());
        usernameField.setOnAction(e -> attemptLogin.run());

        form.getChildren().addAll(userBox, passBox, rememberMeCheck, authErrorBanner, actionRow);

        card.getChildren().addAll(header, form);
        root.getChildren().add(card);
        return root;
    }

    private boolean showFirstLoginPasswordChangeDialog(String currentPassword) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("First-Login Password Update Required");
        dialog.setHeaderText("Security Policy: Please update your initial password before continuing.");

        ButtonType updateBtnType = new ButtonType("Update Password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(updateBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        PasswordField newPass = new PasswordField();
        newPass.setPromptText("Must contain exactly 6 characters");
        newPass.setPrefWidth(280);
        TextField newPassText = new TextField();
        newPassText.setPromptText("Must contain exactly 6 characters");
        newPassText.setPrefWidth(280);
        newPassText.setVisible(false);
        newPassText.setManaged(false);
        newPassText.textProperty().bindBidirectional(newPass.textProperty());

        PasswordField confirmPass = new PasswordField();
        confirmPass.setPromptText("Confirm new 6-character password");
        TextField confirmPassText = new TextField();
        confirmPassText.setPromptText("Confirm new 6-character password");
        confirmPassText.setVisible(false);
        confirmPassText.setManaged(false);
        confirmPassText.textProperty().bindBidirectional(confirmPass.textProperty());

        CheckBox showPassCheck = new CheckBox("Show Passwords");
        showPassCheck.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");
        showPassCheck.setOnAction(e -> {
            boolean show = showPassCheck.isSelected();
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
        errLabel.setStyle("-fx-text-fill: #DC4C4C; -fx-font-size: 12px;");
        errLabel.setWrapText(true);

        grid.add(new Label("New Password:"), 0, 0);
        grid.add(new StackPane(newPass, newPassText), 1, 0);
        grid.add(new Label("Confirm Password:"), 0, 1);
        grid.add(new StackPane(confirmPass, confirmPassText), 1, 1);
        grid.add(showPassCheck, 1, 2);
        grid.add(errLabel, 0, 3, 2, 1);
        dialog.getDialogPane().setContent(grid);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(updateBtnType);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String np = newPass.getText();
            String cp = confirmPass.getText();
            if (np == null || np.length() != 6) {
                errLabel.setText("Password must contain exactly 6 characters.");
                event.consume();
                return;
            }
            if (!np.equals(cp)) {
                errLabel.setText("Passwords do not match.");
                event.consume();
                return;
            }
            try {
                ctx.getAuthService().changeOwnPassword(currentPassword, np, cp);
                AlertHelper.showSuccess("Password Updated", "Your password has been updated securely.");
            } catch (Exception ex) {
                errLabel.setText(GlobalExceptionHandler.handleAndGetUserMessage(ex, "first-login password change"));
                event.consume();
            }
        });

        dialog.setResultConverter(btn -> btn == updateBtnType);
        Optional<Boolean> res = dialog.showAndWait();
        return res.orElse(false);
    }

    private void setFieldError(Control field, Label errorLabel, String message) {
        if (!field.getStyleClass().contains("field-error")) {
            field.getStyleClass().add("field-error");
        }
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearFieldError(Control field, Label errorLabel) {
        field.getStyleClass().remove("field-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
