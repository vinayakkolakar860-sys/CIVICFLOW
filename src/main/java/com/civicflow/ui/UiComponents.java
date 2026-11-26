package com.civicflow.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Reusable JavaFX UI components following the CIVICFLOW Enterprise Design System.
 */
public final class UiComponents {

    private UiComponents() {
    }

    /**
     * Reusable KPI statistic card with top colored accent indicator.
     */
    public static VBox createStatCard(String labelText, String valueText, String accentHex) {
        VBox card = new VBox(8);
        card.getStyleClass().add("stat-card");
        card.setMinWidth(160);
        HBox.setHgrow(card, Priority.ALWAYS);

        Rectangle accent = new Rectangle(36, 4);
        accent.setArcWidth(4);
        accent.setArcHeight(4);
        accent.setFill(Color.web(accentHex));

        Label valLabel = new Label(valueText);
        valLabel.getStyleClass().add("stat-value");

        Label titleLabel = new Label(labelText);
        titleLabel.getStyleClass().add("stat-label");
        titleLabel.setWrapText(true);

        card.getChildren().addAll(accent, valLabel, titleLabel);
        return card;
    }

    /**
     * Color-coded status pill badge.
     */
    public static Label createStatusBadge(String status) {
        String text = status != null ? status : "UNKNOWN";
        Label badge = new Label(text);
        badge.getStyleClass().add("badge");

        switch (text.toUpperCase()) {
            case "ACTIVE", "APPROVED", "OPEN", "SUCCESS", "SUBMITTED", "LEADING" -> badge.getStyleClass().add("badge-success");
            case "PUBLISHED", "SUPER_ADMIN", "TIED FOR LEAD" -> badge.getStyleClass().add("badge-primary");
            case "SCHEDULED", "ELECTION_ADMIN", "PARTICIPATING" -> badge.getStyleClass().add("badge-accent");
            case "PENDING", "DRAFT", "CLOSED" -> badge.getStyleClass().add("badge-warning");
            case "REJECTED", "SUSPENDED", "LOCKED", "CANCELLED", "FAILURE", "DENIED", "WITHDRAWN" -> badge.getStyleClass().add("badge-danger");
            default -> badge.getStyleClass().add("badge-neutral");
        }
        return badge;
    }

    /**
     * Clear educational simulation / demo data disclaimer banner.
     */
    public static HBox createDemoBanner(String text) {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("demo-banner");

        Label tag = new Label("DEMO MODE");
        tag.setStyle("-fx-background-color: #D97706; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 10px; -fx-padding: 2 8 2 8; -fx-background-radius: 4;");

        Label msg = new Label(text);
        msg.getStyleClass().add("demo-banner-text");
        msg.setWrapText(true);

        box.getChildren().addAll(tag, msg);
        return box;
    }

    /**
     * Helpful empty state placeholder for tables or sections.
     */
    public static VBox createEmptyState(String title, String subtitle) {
        VBox box = new VBox(6);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(32));

        Label t = new Label(title);
        t.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        Label s = new Label(subtitle);
        s.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #697586;");
        s.setWrapText(true);

        box.getChildren().addAll(t, s);
        return box;
    }

    /**
     * Reusable loading overlay displayed during background JDBC or report operations.
     */
    public static StackPane createLoadingOverlay() {
        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("loading-overlay");
        VBox spinnerBox = new VBox(10);
        spinnerBox.setAlignment(Pos.CENTER);
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setMaxSize(44, 44);
        Label lbl = new Label("Processing...");
        lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #2855D9;");
        spinnerBox.getChildren().addAll(indicator, lbl);
        overlay.getChildren().add(spinnerBox);
        overlay.setVisible(false);
        return overlay;
    }

    /**
     * Reusable pagination control bar.
     */
    public static class PaginationBar extends HBox {
        private final Button prevBtn = new Button("← Previous");
        private final Button nextBtn = new Button("Next →");
        private final Label infoLabel = new Label("Page 1 of 1 (0 records)");
        private int currentPage = 1;
        private int totalPages = 1;
        private Runnable onPageChange;

        public PaginationBar() {
            super(12);
            setAlignment(Pos.CENTER_RIGHT);
            setPadding(new Insets(8, 0, 0, 0));

            prevBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
            nextBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
            infoLabel.setStyle("-fx-text-fill: #697586; -fx-font-size: 12px;");

            prevBtn.setOnAction(e -> {
                if (currentPage > 1) {
                    currentPage--;
                    if (onPageChange != null) onPageChange.run();
                }
            });
            nextBtn.setOnAction(e -> {
                if (currentPage < totalPages) {
                    currentPage++;
                    if (onPageChange != null) onPageChange.run();
                }
            });

            getChildren().addAll(infoLabel, prevBtn, nextBtn);
            update(1, 1, 0);
        }

        public void setOnPageChange(Runnable onPageChange) {
            this.onPageChange = onPageChange;
        }

        public int getCurrentPage() {
            return currentPage;
        }

        public void resetToFirstPage() {
            this.currentPage = 1;
        }

        public void update(int page, int totalPages, long totalCount) {
            this.currentPage = Math.max(1, page);
            this.totalPages = Math.max(1, totalPages);
            infoLabel.setText("Page " + this.currentPage + " of " + this.totalPages + " (" + totalCount + " records)");
            prevBtn.setDisable(this.currentPage <= 1);
            nextBtn.setDisable(this.currentPage >= this.totalPages);
        }
    }

    public static VBox wrapInCard(String title, Node content) {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        if (title != null && !title.isBlank()) {
            Label h = new Label(title);
            h.getStyleClass().add("section-heading");
            card.getChildren().add(h);
        }
        card.getChildren().add(content);
        return card;
    }
}
