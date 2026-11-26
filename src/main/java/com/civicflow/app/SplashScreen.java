package com.civicflow.app;

import com.civicflow.ui.CivicFlowLogo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Branded CIVICFLOW startup splash screen shown while establishing the JDBC pool.
 */
public class SplashScreen {

    private final Stage stage;
    private final Label statusLabel = new Label("Initializing CIVICFLOW Election Management Suite...");
    private final ProgressBar progressBar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);

    public SplashScreen() {
        stage = new Stage(StageStyle.UNDECORATED);
        stage.getIcons().add(CivicFlowLogo.createAppIcon());

        VBox root = new VBox(18);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(36));
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #111827, #1E293B, #1D3E9E);"
                + "-fx-border-color: #2855D9; -fx-border-width: 1.5; -fx-background-radius: 12; -fx-border-radius: 12;");

        HBox brandRow = new HBox(16);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        VBox titles = new VBox(2);
        Label title = new Label("CIVICFLOW");
        title.setTextFill(Color.WHITE);
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 30));
        Label sub = new Label("Election Management Suite (CEMS)");
        sub.setTextFill(Color.web("#93C5FD"));
        sub.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13.5));
        titles.getChildren().addAll(title, sub);
        brandRow.getChildren().addAll(CivicFlowLogo.createLogoMark(54), titles);

        Label notice = new Label("Educational Election Simulation Platform • Enterprise Desktop Edition");
        notice.setTextFill(Color.web("#CBD5E1"));
        notice.setFont(Font.font("Segoe UI", 12.5));

        progressBar.setPrefWidth(420);
        progressBar.setPrefHeight(8);
        progressBar.setStyle("-fx-accent: #17A6A1;");

        statusLabel.setTextFill(Color.web("#E2E8F0"));
        statusLabel.setFont(Font.font("Segoe UI", 12));

        root.getChildren().addAll(brandRow, notice, progressBar, statusLabel);

        Scene scene = new Scene(root, 500, 240);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.centerOnScreen();
    }

    public void show() {
        stage.show();
    }

    public void updateStatus(String text, double progress) {
        statusLabel.setText(text);
        progressBar.setProgress(progress);
    }

    public void close() {
        stage.close();
    }
}
