package com.civicflow.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.util.Optional;

/**
 * Standardized user-facing alert and confirmation dialogs.
 */
public final class AlertHelper {

    private AlertHelper() {
    }

    public static void showInfo(String title, String message) {
        Alert alert = createStyledAlert(Alert.AlertType.INFORMATION, title, message);
        alert.showAndWait();
    }

    public static void showSuccess(String title, String message) {
        Alert alert = createStyledAlert(Alert.AlertType.INFORMATION, title, message);
        alert.showAndWait();
    }

    public static void showWarning(String title, String message) {
        Alert alert = createStyledAlert(Alert.AlertType.WARNING, title, message);
        alert.showAndWait();
    }

    public static void showError(String title, String message) {
        Alert alert = createStyledAlert(Alert.AlertType.ERROR, title, message);
        alert.showAndWait();
    }

    public static boolean confirm(String title, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        styleAlert(alert);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private static Alert createStyledAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        styleAlert(alert);
        return alert;
    }

    private static void styleAlert(Alert alert) {
        if (alert.getDialogPane().getScene() != null && alert.getDialogPane().getScene().getWindow() instanceof Stage st) {
            st.getIcons().add(CivicFlowLogo.createAppIcon());
        }
        String css = AlertHelper.class.getResource("/css/civicflow.css") != null
                ? AlertHelper.class.getResource("/css/civicflow.css").toExternalForm()
                : null;
        if (css != null) {
            alert.getDialogPane().getStylesheets().add(css);
        }
        alert.getDialogPane().setMinWidth(420);
    }
}
