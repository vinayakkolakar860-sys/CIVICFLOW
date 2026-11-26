package com.civicflow.controller;

import com.civicflow.app.ApplicationContext;
import com.civicflow.model.Constituency;
import com.civicflow.model.Election;
import com.civicflow.reporting.ReportData;
import com.civicflow.reporting.ReportType;
import com.civicflow.security.CsvSanitizer;
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
import java.util.List;
import java.util.function.Consumer;

/**
 * Full Reporting & Export Center supporting all 11 CIVICFLOW reports,
 * live preview tables, date/election/constituency filters, and secure PDF/CSV export.
 */
public class ReportsView {

    private final ApplicationContext ctx;
    private final Consumer<Boolean> loadingToggle;

    private final ComboBox<ReportType> reportTypeBox = new ComboBox<>(FXCollections.observableArrayList(ReportType.values()));
    private final TextField searchField = new TextField();
    private final ComboBox<Election> electionFilter = new ComboBox<>();
    private final ComboBox<Constituency> constituencyFilter = new ComboBox<>();
    private final DatePicker fromDate = new DatePicker();
    private final DatePicker toDate = new DatePicker();

    private final Label summaryLabel = new Label("Select a report type and click 'Generate Preview'.");
    private final TableView<List<String>> previewTable = new TableView<>();
    private final Button exportPdfBtn = new Button("Export PDF");
    private final Button exportCsvBtn = new Button("Export CSV");

    private ReportData currentReport;

    public ReportsView(ApplicationContext ctx, Consumer<Boolean> loadingToggle) {
        this.ctx = ctx;
        this.loadingToggle = loadingToggle;
    }

    public Node build() {
        VBox root = new VBox(16);

        reportTypeBox.setValue(ReportType.VOTER_REGISTRATION);
        reportTypeBox.setPrefWidth(260);

        searchField.setPromptText("Optional keyword search...");
        searchField.setPrefWidth(200);

        electionFilter.setPromptText("All Elections");
        electionFilter.setPrefWidth(210);

        constituencyFilter.setPromptText("All Constituencies");
        constituencyFilter.setPrefWidth(190);

        fromDate.setPromptText("From Date");
        fromDate.setPrefWidth(135);
        toDate.setPromptText("To Date");
        toDate.setPrefWidth(135);

        Button generateBtn = new Button("Generate Preview");
        generateBtn.getStyleClass().add("btn-primary");
        generateBtn.setOnAction(e -> generatePreview());

        Button clearBtn = new Button("Clear Filters");
        clearBtn.getStyleClass().add("btn-secondary");
        clearBtn.setOnAction(e -> {
            searchField.clear();
            electionFilter.setValue(null);
            constituencyFilter.setValue(null);
            fromDate.setValue(null);
            toDate.setValue(null);
            generatePreview();
        });

        exportCsvBtn.getStyleClass().add("btn-secondary");
        exportCsvBtn.setDisable(true);
        exportCsvBtn.setOnAction(e -> exportReport(false));

        exportPdfBtn.getStyleClass().add("btn-accent");
        exportPdfBtn.setDisable(true);
        exportPdfBtn.setOnAction(e -> exportReport(true));

        FlowPane filterRow = new FlowPane(10, 10,
                new VBox(4, new Label("Report Type"), reportTypeBox),
                new VBox(4, new Label("Search"), searchField),
                new VBox(4, new Label("Election Filter"), electionFilter),
                new VBox(4, new Label("Constituency Filter"), constituencyFilter),
                new VBox(4, new Label("From Date"), fromDate),
                new VBox(4, new Label("To Date"), toDate)
        );

        HBox actionRow = new HBox(10, generateBtn, clearBtn);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        actionRow.getChildren().addAll(sp, exportCsvBtn, exportPdfBtn);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        VBox filterCard = new VBox(12, filterRow, actionRow);
        filterCard.getStyleClass().add("card");

        previewTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        previewTable.setPrefHeight(440);
        previewTable.setPlaceholder(UiComponents.createEmptyState("No Records", "No records matched your report filters."));

        summaryLabel.setStyle("-fx-font-weight: 600; -fx-text-fill: #334155;");
        VBox previewCard = new VBox(10, summaryLabel, previewTable);
        previewCard.getStyleClass().add("card");

        root.getChildren().addAll(
                UiComponents.createDemoBanner("All exported PDF and CSV reports include an educational simulation watermark and formula-injection protection."),
                filterCard,
                previewCard
        );

        loadFilterMetadata();
        return root;
    }

    private record FilterMeta(List<Election> elections, List<Constituency> constituencies) {}

    private void loadFilterMetadata() {
        AsyncTaskRunner.run(
                () -> new FilterMeta(
                        ctx.getElectionService().getAllElections(null, null),
                        ctx.getConstituencyService().getAll(null, null)),
                meta -> {
                    electionFilter.setItems(FXCollections.observableArrayList(meta.elections()));
                    constituencyFilter.setItems(FXCollections.observableArrayList(meta.constituencies()));
                    generatePreview();
                },
                null, loadingToggle, "load report filters"
        );
    }

    private void generatePreview() {
        ReportType type = reportTypeBox.getValue();
        Long elId = electionFilter.getValue() != null ? electionFilter.getValue().getElectionId() : null;
        Long cId = constituencyFilter.getValue() != null ? constituencyFilter.getValue().getConstituencyId() : null;

        AsyncTaskRunner.run(
                () -> ctx.getReportService().generateReport(type, searchField.getText(), elId, cId, fromDate.getValue(), toDate.getValue()),
                this::renderPreview,
                null, loadingToggle, "generate report preview"
        );
    }

    private void renderPreview(ReportData data) {
        this.currentReport = data;
        exportPdfBtn.setDisable(false);
        exportCsvBtn.setDisable(false);

        summaryLabel.setText(data.getTitle() + " — " + data.getRows().size() + " record(s) matched");
        previewTable.getColumns().clear();

        for (int i = 0; i < data.getHeaders().size(); i++) {
            final int colIdx = i;
            TableColumn<List<String>, String> col = new TableColumn<>(data.getHeaders().get(i));
            col.setCellValueFactory(cell -> {
                List<String> row = cell.getValue();
                return new SimpleStringProperty(colIdx < row.size() && row.get(colIdx) != null ? row.get(colIdx) : "");
            });
            previewTable.getColumns().add(col);
        }
        previewTable.setItems(FXCollections.observableArrayList(data.getRows()));
    }

    private void exportReport(boolean pdf) {
        if (currentReport == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle(pdf ? "Export Report to PDF" : "Export Report to CSV");
        String safeBase = "civicflow_" + reportTypeBox.getValue().name().toLowerCase();
        fc.setInitialFileName(CsvSanitizer.sanitizeFileName(safeBase, pdf ? ".pdf" : ".csv"));
        fc.getExtensionFilters().add(pdf
                ? new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
                : new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showSaveDialog(previewTable.getScene().getWindow());
        if (file == null) return;

        AsyncTaskRunner.run(
                () -> pdf ? ctx.getReportService().exportPdf(currentReport, file.toPath())
                        : ctx.getReportService().exportCsv(currentReport, file.toPath()),
                saved -> AlertHelper.showSuccess("Export Saved", "Report successfully exported to:\n" + saved.toAbsolutePath()),
                null, loadingToggle, "export report"
        );
    }
}
