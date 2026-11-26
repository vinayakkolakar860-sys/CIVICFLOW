package com.civicflow.controller;

import com.civicflow.app.ApplicationContext;
import com.civicflow.model.*;
import com.civicflow.reporting.ReportData;
import com.civicflow.security.CsvSanitizer;
import com.civicflow.ui.AlertHelper;
import com.civicflow.ui.AsyncTaskRunner;
import com.civicflow.ui.UiComponents;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Election Results Dashboard displaying database-aggregated simulated results, charts,
 * constituency breakdowns, publication controls, and PDF/CSV export.
 */
public class ResultsDashboardView {

    private final ApplicationContext ctx;
    private final Long preselectedElectionId;
    private final Consumer<Boolean> loadingToggle;

    private final ComboBox<Election> electionSelector = new ComboBox<>();
    private final Button publishBtn = new Button("Publish Results");
    private final Button pdfBtn = new Button("Export PDF");
    private final Button csvBtn = new Button("Export CSV");
    private final VBox resultsContainer = new VBox(16);

    private ElectionResultSummary currentSummary;

    public ResultsDashboardView(ApplicationContext ctx, Long preselectedElectionId, Consumer<Boolean> loadingToggle) {
        this.ctx = ctx;
        this.preselectedElectionId = preselectedElectionId;
        this.loadingToggle = loadingToggle;
    }

    public Node build() {
        VBox root = new VBox(16);

        HBox banner = UiComponents.createDemoBanner(
                "DEMONSTRATION / SIMULATED RESULTS — Calculated live from anonymous_ballots in MySQL. Not an official public election tally.");

        electionSelector.setPromptText("Select a Closed or Published Election...");
        electionSelector.setPrefWidth(360);
        electionSelector.setOnAction(e -> {
            Election sel = electionSelector.getValue();
            if (sel != null) loadResults(sel.getElectionId());
        });

        boolean isAdmin = ctx.getSessionManager().getCurrentSession()
                .map(s -> s.getRole().isAdmin()).orElse(false);

        publishBtn.getStyleClass().add("btn-success");
        publishBtn.setVisible(isAdmin);
        publishBtn.setManaged(isAdmin);
        publishBtn.setDisable(true);
        publishBtn.setOnAction(e -> handlePublish());

        csvBtn.getStyleClass().add("btn-secondary");
        csvBtn.setDisable(true);
        csvBtn.setOnAction(e -> exportCurrent(false));

        pdfBtn.getStyleClass().add("btn-primary");
        pdfBtn.setDisable(true);
        pdfBtn.setOnAction(e -> exportCurrent(true));

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        HBox topBar = new HBox(10, new Label("Election:"), electionSelector, sp, publishBtn, csvBtn, pdfBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        VBox selectorCard = new VBox(topBar);
        selectorCard.getStyleClass().add("card");

        root.getChildren().addAll(banner, selectorCard, resultsContainer);
        loadSelectableElections();
        return root;
    }

    private void loadSelectableElections() {
        AsyncTaskRunner.run(
                () -> ctx.getResultService().getViewableResultElections(),
                elections -> {
                    electionSelector.setItems(FXCollections.observableArrayList(elections));
                    if (elections.isEmpty()) {
                        resultsContainer.getChildren().setAll(UiComponents.createEmptyState(
                                "No Election Results Available",
                                "Published results (or closed elections for administrators) will appear here."));
                        return;
                    }
                    Election toSelect = elections.get(0);
                    if (preselectedElectionId != null) {
                        toSelect = elections.stream()
                                .filter(e -> e.getElectionId() == preselectedElectionId)
                                .findFirst().orElse(toSelect);
                    }
                    electionSelector.setValue(toSelect);
                    loadResults(toSelect.getElectionId());
                },
                null, loadingToggle, "load result elections"
        );
    }

    private void loadResults(long electionId) {
        AsyncTaskRunner.run(
                () -> ctx.getResultService().getElectionResults(electionId),
                this::renderSummary,
                null, loadingToggle, "calculate election results"
        );
    }

    private void renderSummary(ElectionResultSummary summary) {
        this.currentSummary = summary;
        resultsContainer.getChildren().clear();

        pdfBtn.setDisable(false);
        csvBtn.setDisable(false);
        publishBtn.setDisable(summary.isPublished() || summary.getElection().getStatus() != ElectionStatus.CLOSED);

        // KPI Summary Row
        HBox kpiRow = new HBox(14,
                UiComponents.createStatCard("Publication Status", summary.isPublished() ? "PUBLISHED" : "CLOSED (UNPUBLISHED)",
                        summary.isPublished() ? "#16A36A" : "#E9A23B"),
                UiComponents.createStatCard("Eligible Voters", String.valueOf(summary.getTotalEligibleVoters()), "#2855D9"),
                UiComponents.createStatCard("Simulated Ballots Cast", String.valueOf(summary.getTotalBallotsCast()), "#17A6A1"),
                UiComponents.createStatCard("Turnout Rate", String.format("%.2f%%", summary.getTurnoutPercentage()), "#6366F1"),
                UiComponents.createStatCard("Leading Party", summary.getLeadingPartySummary() != null ? summary.getLeadingPartySummary() : "—", "#16A36A")
        );

        // Charts Row: BarChart + PieChart
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Candidate");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Simulated Votes");
        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle("Simulated Votes by Candidate");
        barChart.setLegendVisible(false);
        barChart.setAnimated(false);
        barChart.setPrefHeight(290);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        PieChart pieChart = new PieChart();
        pieChart.setTitle("Vote Share Distribution");
        pieChart.setPrefHeight(290);

        for (CandidateResult cr : summary.getCandidateResults()) {
            String label = cr.getCandidateName() + " (" + cr.getPartyCode() + ")";
            series.getData().add(new XYChart.Data<>(label, cr.getVoteCount()));
            if (cr.getVoteCount() > 0) {
                pieChart.getData().add(new PieChart.Data(label + " [" + cr.getVoteCount() + "]", cr.getVoteCount()));
            }
        }
        barChart.getData().add(series);
        if (pieChart.getData().isEmpty()) {
            pieChart.getData().add(new PieChart.Data("No Ballots Cast (0)", 1));
        }

        VBox barCard = new VBox(barChart);
        barCard.getStyleClass().add("card");
        HBox.setHgrow(barCard, Priority.ALWAYS);
        VBox pieCard = new VBox(pieChart);
        pieCard.getStyleClass().add("card");
        HBox.setHgrow(pieCard, Priority.ALWAYS);
        HBox chartsRow1 = new HBox(16, barCard, pieCard);

        // Charts Row 2: Party Distribution + Constituency Turnout
        PieChart partyPie = new PieChart();
        partyPie.setTitle("Simulated Vote Distribution by Party");
        partyPie.setPrefHeight(290);
        for (PartyResult pr : summary.getPartyResults()) {
            if (pr.getTotalVotes() > 0) {
                String pLabel = pr.getPartyCode() + " [" + pr.getTotalVotes() + " votes, " + String.format("%.1f%%", pr.getVotePercentage()) + "]";
                partyPie.getData().add(new PieChart.Data(pLabel, pr.getTotalVotes()));
            }
        }
        if (partyPie.getData().isEmpty()) {
            partyPie.getData().add(new PieChart.Data("No Party Votes Recorded", 1));
        }

        CategoryAxis constXAxis = new CategoryAxis();
        constXAxis.setLabel("Constituency");
        NumberAxis constYAxis = new NumberAxis(0, 100, 20);
        constYAxis.setLabel("Turnout (%)");
        BarChart<String, Number> constTurnoutChart = new BarChart<>(constXAxis, constYAxis);
        constTurnoutChart.setTitle("Constituency Turnout Percentage");
        constTurnoutChart.setLegendVisible(false);
        constTurnoutChart.setAnimated(false);
        constTurnoutChart.setPrefHeight(290);

        XYChart.Series<String, Number> turnoutSeries = new XYChart.Series<>();
        for (ConstituencyResult cr : summary.getConstituencyResults()) {
            turnoutSeries.getData().add(new XYChart.Data<>(cr.getConstituencyName(), cr.getTurnoutPercentage()));
        }
        if (turnoutSeries.getData().isEmpty()) {
            turnoutSeries.getData().add(new XYChart.Data<>("None", 0));
        }
        constTurnoutChart.getData().add(turnoutSeries);

        VBox partyCard = new VBox(partyPie);
        partyCard.getStyleClass().add("card");
        HBox.setHgrow(partyCard, Priority.ALWAYS);
        VBox constCard = new VBox(constTurnoutChart);
        constCard.getStyleClass().add("card");
        HBox.setHgrow(constCard, Priority.ALWAYS);
        HBox chartsRow2 = new HBox(16, partyCard, constCard);

        // Candidate Result Table
        TableView<CandidateResult> candTable = new TableView<>();
        candTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        candTable.setPrefHeight(240);
        candTable.setPlaceholder(UiComponents.createEmptyState("No Candidates", "No candidates were assigned to this election."));

        TableColumn<CandidateResult, String> cConst = new TableColumn<>("Constituency");
        cConst.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getConstituencyName()));
        TableColumn<CandidateResult, String> cName = new TableColumn<>("Candidate");
        cName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCandidateName()));
        TableColumn<CandidateResult, String> cParty = new TableColumn<>("Party");
        cParty.setCellValueFactory(d -> {
            CandidateResult cr = d.getValue();
            String pDisp = cr.getPartyCode() != null && cr.getPartyCode().equalsIgnoreCase(cr.getPartyName())
                    ? cr.getPartyName()
                    : cr.getPartyName() + " (" + cr.getPartyCode() + ")";
            return new SimpleStringProperty(pDisp);
        });
        TableColumn<CandidateResult, String> cVotes = new TableColumn<>("Simulated Votes");
        cVotes.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getVoteCount())));
        TableColumn<CandidateResult, String> cPct = new TableColumn<>("Constituency Share");
        cPct.setCellValueFactory(d -> new SimpleStringProperty(String.format("%.2f%%", d.getValue().getPercentage())));
        candTable.getColumns().addAll(cConst, cName, cParty, cVotes, cPct);
        candTable.setItems(FXCollections.observableArrayList(summary.getCandidateResults()));

        // Constituency Breakdown Table
        TableView<ConstituencyResult> constTable = new TableView<>();
        constTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        constTable.setPrefHeight(200);

        TableColumn<ConstituencyResult, String> ccCode = new TableColumn<>("Code");
        ccCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getConstituencyCode()));
        TableColumn<ConstituencyResult, String> ccName = new TableColumn<>("Constituency");
        ccName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getConstituencyName()));
        TableColumn<ConstituencyResult, String> ccElig = new TableColumn<>("Eligible");
        ccElig.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getEligibleVoters())));
        TableColumn<ConstituencyResult, String> ccBallots = new TableColumn<>("Ballots Cast");
        ccBallots.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getTotalBallots())));
        TableColumn<ConstituencyResult, String> ccTurn = new TableColumn<>("Turnout");
        ccTurn.setCellValueFactory(d -> new SimpleStringProperty(String.format("%.2f%%", d.getValue().getTurnoutPercentage())));
        TableColumn<ConstituencyResult, String> ccLead = new TableColumn<>("Leading Candidate");
        ccLead.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getLeadingCandidateName()));
        constTable.getColumns().addAll(ccCode, ccName, ccElig, ccBallots, ccTurn, ccLead);
        constTable.setItems(FXCollections.observableArrayList(summary.getConstituencyResults()));

        // Party-Wise Simulated Results Table
        TableView<PartyResult> partyTable = new TableView<>();
        partyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        partyTable.setPrefHeight(220);
        partyTable.setPlaceholder(UiComponents.createEmptyState("No Parties", "No parties were assigned to candidates in this election."));

        TableColumn<PartyResult, String> pCode = new TableColumn<>("Party Code");
        pCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPartyCode()));
        TableColumn<PartyResult, String> pName = new TableColumn<>("Party Name");
        pName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPartyName()));
        TableColumn<PartyResult, String> pCands = new TableColumn<>("Candidates Fielded");
        pCands.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getCandidateCount())));
        TableColumn<PartyResult, String> pVotes = new TableColumn<>("Total Simulated Votes");
        pVotes.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getTotalVotes())));
        TableColumn<PartyResult, String> pShare = new TableColumn<>("Vote Share (%)");
        pShare.setCellValueFactory(d -> new SimpleStringProperty(String.format("%.2f%%", d.getValue().getVotePercentage())));
        TableColumn<PartyResult, String> pStanding = new TableColumn<>("Standing");
        pStanding.setCellValueFactory(d -> {
            PartyResult pr = d.getValue();
            if (pr.isTiedForLead()) return new SimpleStringProperty("TIED FOR LEAD");
            if (pr.isLeading()) return new SimpleStringProperty("LEADING");
            return new SimpleStringProperty(pr.getTotalVotes() > 0 ? "PARTICIPATING" : "NO VOTES");
        });
        pStanding.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    setGraphic(UiComponents.createStatusBadge(item));
                    setText(null);
                }
            }
        });
        partyTable.getColumns().addAll(pCode, pName, pCands, pVotes, pShare, pStanding);
        partyTable.setItems(FXCollections.observableArrayList(summary.getPartyResults()));

        resultsContainer.getChildren().addAll(
                kpiRow,
                chartsRow1,
                chartsRow2,
                UiComponents.wrapInCard("Party-Wise Simulated Results & Standings (Database-Aggregated)", partyTable),
                UiComponents.wrapInCard("Candidate-Wise Simulated Results", candTable),
                UiComponents.wrapInCard("Constituency-Wise Summary", constTable)
        );
    }

    private void handlePublish() {
        if (currentSummary == null) return;
        Election el = currentSummary.getElection();
        if (!AlertHelper.confirm("Publish Simulated Results",
                "Publish results for " + el.getElectionName() + "?",
                "Once published, voters can view these simulated results and the election becomes immutable.")) return;

        AsyncTaskRunner.run(
                () -> { ctx.getElectionService().publishResults(el.getElectionId()); return null; },
                ignored -> {
                    AlertHelper.showSuccess("Results Published", "Simulated election results are now visible to voters.");
                    loadSelectableElections();
                },
                null, loadingToggle, "publish election results"
        );
    }

    private void exportCurrent(boolean pdf) {
        if (currentSummary == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle(pdf ? "Export Election Results PDF" : "Export Election Results CSV");
        String baseName = "civicflow_results_" + currentSummary.getElection().getElectionCode();
        fc.setInitialFileName(CsvSanitizer.sanitizeFileName(baseName, pdf ? ".pdf" : ".csv"));
        fc.getExtensionFilters().add(pdf
                ? new FileChooser.ExtensionFilter("PDF Documents", "*.pdf")
                : new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showSaveDialog(resultsContainer.getScene().getWindow());
        if (file == null) return;

        long eid = currentSummary.getElection().getElectionId();
        AsyncTaskRunner.run(
                () -> {
                    ReportData data = ctx.getReportService().buildElectionResultReportForCurrentUser(eid);
                    return pdf ? ctx.getReportService().exportPdf(data, file.toPath())
                            : ctx.getReportService().exportCsv(data, file.toPath());
                },
                saved -> AlertHelper.showSuccess("Export Complete", "Results saved to:\n" + saved.toAbsolutePath()),
                null, loadingToggle, "export election results"
        );
    }
}
