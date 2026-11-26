package com.civicflow.controller;

import com.civicflow.app.ApplicationContext;
import com.civicflow.model.AuditLog;
import com.civicflow.reporting.ReportData;
import com.civicflow.reporting.ReportType;
import com.civicflow.security.CsvSanitizer;
import com.civicflow.service.SettingsService;
import com.civicflow.ui.AlertHelper;
import com.civicflow.ui.AsyncTaskRunner;
import com.civicflow.ui.UiComponents;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Read-only paginated Audit Log Viewer with module, search, and date range filtering.
 */
public class AuditLogView {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApplicationContext ctx;
    private final Consumer<Boolean> loadingToggle;

    private final ComboBox<String> moduleFilter = new ComboBox<>();
    private final TextField searchField = new TextField();
    private final DatePicker fromDate = new DatePicker();
    private final DatePicker toDate = new DatePicker();
    private final TableView<AuditLog> table = new TableView<>();
    private final UiComponents.PaginationBar paginationBar = new UiComponents.PaginationBar();

    public AuditLogView(ApplicationContext ctx, Consumer<Boolean> loadingToggle) {
        this.ctx = ctx;
        this.loadingToggle = loadingToggle;
    }

    public Node build() {
        VBox root = new VBox(16);

        moduleFilter.getItems().setAll("ALL", "AUTH", "USER_ADMIN", "VOTER", "CANDIDATE", "PARTY", "CONSTITUENCY", "ELECTION", "VOTING", "REPORTS", "SETTINGS");
        moduleFilter.setValue("ALL");
        moduleFilter.setOnAction(e -> { paginationBar.resetToFirstPage(); loadPage(); });

        searchField.setPromptText("Search actor, action, details...");
        searchField.setPrefWidth(220);
        searchField.setOnAction(e -> { paginationBar.resetToFirstPage(); loadPage(); });

        fromDate.setPromptText("From");
        fromDate.setPrefWidth(130);
        toDate.setPromptText("To");
        toDate.setPrefWidth(130);

        Button filterBtn = new Button("Apply");
        filterBtn.getStyleClass().add("btn-secondary");
        filterBtn.setOnAction(e -> { paginationBar.resetToFirstPage(); loadPage(); });

        Button resetBtn = new Button("Reset");
        resetBtn.getStyleClass().add("btn-secondary");
        resetBtn.setOnAction(e -> {
            moduleFilter.setValue("ALL");
            searchField.clear();
            fromDate.setValue(null);
            toDate.setValue(null);
            paginationBar.resetToFirstPage();
            loadPage();
        });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button csvBtn = new Button("Export CSV");
        csvBtn.getStyleClass().add("btn-secondary");
        csvBtn.setOnAction(e -> exportAudit(false));

        Button pdfBtn = new Button("Export PDF");
        pdfBtn.getStyleClass().add("btn-primary");
        pdfBtn.setOnAction(e -> exportAudit(true));

        HBox toolbar = new HBox(10, moduleFilter, searchField, fromDate, toDate, filterBtn, resetBtn, sp, csvBtn, pdfBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(480);
        table.setPlaceholder(UiComponents.createEmptyState("No Audit Records", "Security and governance events will appear here."));

        TableColumn<AuditLog, String> cId = new TableColumn<>("ID");
        cId.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getAuditId())));
        cId.setPrefWidth(65);
        TableColumn<AuditLog, String> cTs = new TableColumn<>("Timestamp");
        cTs.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTimestamp() != null ? d.getValue().getTimestamp().format(TS_FMT) : ""));
        cTs.setPrefWidth(155);
        TableColumn<AuditLog, String> cActor = new TableColumn<>("Actor");
        cActor.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUsername() != null ? d.getValue().getUsername() : "SYSTEM"));
        cActor.setPrefWidth(120);
        TableColumn<AuditLog, String> cMod = new TableColumn<>("Module");
        cMod.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getModule()));
        cMod.setPrefWidth(110);
        TableColumn<AuditLog, String> cAct = new TableColumn<>("Action");
        cAct.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAction()));
        cAct.setPrefWidth(180);
        TableColumn<AuditLog, String> cDet = new TableColumn<>("Non-Sensitive Details");
        cDet.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDetails()));
        cDet.setPrefWidth(280);
        TableColumn<AuditLog, String> cRes = new TableColumn<>("Status");
        cRes.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getResultStatus().name()));
        cRes.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : UiComponents.createStatusBadge(item));
            }
        });
        cRes.setPrefWidth(95);

        table.getColumns().addAll(cId, cTs, cActor, cMod, cAct, cDet, cRes);
        paginationBar.setOnPageChange(this::loadPage);

        VBox card = new VBox(12, toolbar, table, paginationBar);
        card.getStyleClass().add("card");

        Label note = new Label("Audit logs are append-only from the UI and strictly exclude passwords, password hashes, and ballot selections.");
        note.setStyle("-fx-text-fill: #697586; -fx-font-size: 12px;");

        root.getChildren().addAll(card, note);
        loadPage();
        return root;
    }

    private void loadPage() {
        int pageSize = ctx.getSettingsService().getIntSetting(SettingsService.KEY_PAGE_SIZE, 25);
        AsyncTaskRunner.run(
                () -> ctx.getAuditService().searchLogs(moduleFilter.getValue(), searchField.getText(),
                        fromDate.getValue(), toDate.getValue(), paginationBar.getCurrentPage(), pageSize),
                page -> {
                    table.setItems(FXCollections.observableArrayList(page.getItems()));
                    paginationBar.update(page.getPageNumber(), page.getTotalPages(), page.getTotalCount());
                },
                null, loadingToggle, "load audit logs"
        );
    }

    private void exportAudit(boolean pdf) {
        FileChooser fc = new FileChooser();
        fc.setTitle(pdf ? "Export Audit Logs (PDF)" : "Export Audit Logs (CSV)");
        fc.setInitialFileName(CsvSanitizer.sanitizeFileName("civicflow_audit_logs", pdf ? ".pdf" : ".csv"));
        fc.getExtensionFilters().add(pdf
                ? new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
                : new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showSaveDialog(table.getScene().getWindow());
        if (file == null) return;

        AsyncTaskRunner.run(
                () -> {
                    ReportData data = ctx.getReportService().generateReport(
                            ReportType.AUDIT_LOG, searchField.getText(), null, null, fromDate.getValue(), toDate.getValue());
                    return pdf ? ctx.getReportService().exportPdf(data, file.toPath())
                            : ctx.getReportService().exportCsv(data, file.toPath());
                },
                saved -> AlertHelper.showSuccess("Exported", "Audit report saved to:\n" + saved.toAbsolutePath()),
                null, loadingToggle, "export audit logs"
        );
    }
}
