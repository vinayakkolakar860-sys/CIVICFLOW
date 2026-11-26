package com.civicflow.controller;

import com.civicflow.app.ApplicationContext;
import com.civicflow.model.AuditLog;
import com.civicflow.model.DashboardStats;
import com.civicflow.navigation.ViewNavigator;
import com.civicflow.ui.AsyncTaskRunner;
import com.civicflow.ui.UiComponents;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Admin Dashboard displaying real-time MySQL KPIs, JavaFX charts, quick actions, and recent audit activity.
 */
public class AdminDashboardView {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ApplicationContext ctx;
    private final ViewNavigator navigator;
    private final Consumer<Boolean> loadingToggle;

    private final VBox container = new VBox(18);

    public AdminDashboardView(ApplicationContext ctx, ViewNavigator navigator, Consumer<Boolean> loadingToggle) {
        this.ctx = ctx;
        this.navigator = navigator;
        this.loadingToggle = loadingToggle;
    }

    public Node build() {
        refresh();
        return container;
    }

    private void refresh() {
        AsyncTaskRunner.run(
                () -> ctx.getDashboardService().loadAdminDashboardStats(),
                this::renderDashboard,
                null,
                loadingToggle,
                "load admin dashboard"
        );
    }

    private void renderDashboard(DashboardStats stats) {
        container.getChildren().clear();

        // Top Demo Banner & Quick Actions Row
        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);
        HBox banner = UiComponents.createDemoBanner("DEMO DATA — All metrics below are queried live from the local MySQL civicflow_db database.");
        HBox.setHgrow(banner, Priority.ALWAYS);

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setOnAction(e -> refresh());

        Button newElectionBtn = new Button("+ Manage Elections");
        newElectionBtn.getStyleClass().add("btn-primary");
        newElectionBtn.setOnAction(e -> navigator.navigateTo(MainShellView.ROUTE_ELECTIONS));

        Button reviewVotersBtn = new Button("Review Voters (" + stats.getPendingVoters() + ")");
        reviewVotersBtn.getStyleClass().add("btn-accent");
        reviewVotersBtn.setOnAction(e -> navigator.navigateTo(MainShellView.ROUTE_VOTERS));

        topRow.getChildren().addAll(banner, reviewVotersBtn, newElectionBtn, refreshBtn);

        // Row 1 KPIs (5 cards)
        HBox kpiRow1 = new HBox(14,
                UiComponents.createStatCard("Total Voters", String.valueOf(stats.getTotalVoters()), "#2855D9"),
                UiComponents.createStatCard("Verified Voters", String.valueOf(stats.getVerifiedVoters()), "#16A36A"),
                UiComponents.createStatCard("Pending Approvals", String.valueOf(stats.getPendingVoters()), "#E9A23B"),
                UiComponents.createStatCard("Total Candidates", String.valueOf(stats.getTotalCandidates()), "#17A6A1"),
                UiComponents.createStatCard("Constituencies", String.valueOf(stats.getTotalConstituencies()), "#6366F1")
        );

        // Row 2 KPIs (5 cards)
        HBox kpiRow2 = new HBox(14,
                UiComponents.createStatCard("Total Elections", String.valueOf(stats.getTotalElections()), "#2855D9"),
                UiComponents.createStatCard("Upcoming Scheduled", String.valueOf(stats.getUpcomingElections()), "#17A6A1"),
                UiComponents.createStatCard("Open Simulations", String.valueOf(stats.getOpenElections()), "#16A36A"),
                UiComponents.createStatCard("Closed Elections", String.valueOf(stats.getClosedElections()), "#E9A23B"),
                UiComponents.createStatCard("Published Results", String.valueOf(stats.getPublishedResults()), "#0EA5E9")
        );

        // Charts Row 1: Election Status Pie + Voter Verification Bar
        PieChart electionPie = new PieChart();
        electionPie.setTitle("Elections by Lifecycle Status");
        electionPie.setLegendVisible(true);
        electionPie.setPrefHeight(280);
        stats.getElectionsByStatus().forEach((k, v) -> {
            if (v > 0) electionPie.getData().add(new PieChart.Data(k + " (" + v + ")", v));
        });
        if (electionPie.getData().isEmpty()) {
            electionPie.getData().add(new PieChart.Data("No Elections", 1));
        }

        BarChart<String, Number> voterChart = createBarChart("Voter Registration Status", "Status", "Voters", stats.getVotersByVerificationStatus());
        voterChart.setPrefHeight(280);

        HBox chartsRow1 = new HBox(16,
                wrapChartCard(electionPie),
                wrapChartCard(voterChart)
        );

        // Charts Row 2: Voters by Constituency + Candidates by Fictional Party
        BarChart<String, Number> constChart = createBarChart("Voters by Constituency", "Constituency", "Voters", stats.getVotersByConstituency());
        constChart.setPrefHeight(280);
        BarChart<String, Number> partyChart = createBarChart("Candidate Distribution by Party", "Party", "Candidates", stats.getCandidatesByParty());
        partyChart.setPrefHeight(280);

        HBox chartsRow2 = new HBox(16,
                wrapChartCard(constChart),
                wrapChartCard(partyChart)
        );

        // Historical Participation Chart
        BarChart<String, Number> participationChart = createBarChart("Simulated Participation by Election", "Election", "Participants", stats.getParticipationByElection());
        participationChart.setPrefHeight(260);
        VBox participationCard = UiComponents.wrapInCard("Historical Simulated Participation", participationChart);

        // Recent Activity Table
        TableView<AuditLog> auditTable = new TableView<>();
        auditTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        auditTable.setPrefHeight(260);
        auditTable.setPlaceholder(UiComponents.createEmptyState("No Recent Activity", "Administrative and security events will appear here."));

        TableColumn<AuditLog, String> colTime = new TableColumn<>("Timestamp");
        colTime.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getTimestamp() != null ? d.getValue().getTimestamp().format(TS_FMT) : ""));
        colTime.setPrefWidth(140);

        TableColumn<AuditLog, String> colUser = new TableColumn<>("Actor");
        colUser.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getUsername() != null ? d.getValue().getUsername() : "SYSTEM"));
        colUser.setPrefWidth(120);

        TableColumn<AuditLog, String> colMod = new TableColumn<>("Module");
        colMod.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getModule()));
        colMod.setPrefWidth(110);

        TableColumn<AuditLog, String> colAct = new TableColumn<>("Action");
        colAct.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAction()));
        colAct.setPrefWidth(170);

        TableColumn<AuditLog, String> colDet = new TableColumn<>("Details");
        colDet.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDetails()));
        colDet.setPrefWidth(280);

        TableColumn<AuditLog, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getResultStatus().name()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : UiComponents.createStatusBadge(item));
            }
        });
        colStatus.setPrefWidth(100);

        auditTable.getColumns().addAll(colTime, colUser, colMod, colAct, colDet, colStatus);
        auditTable.setItems(FXCollections.observableArrayList(stats.getRecentActivity()));

        HBox activityHeader = new HBox(10);
        activityHeader.setAlignment(Pos.CENTER_LEFT);
        Label actTitle = new Label("Recent System & Audit Activity");
        actTitle.getStyleClass().add("section-heading");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button viewAllAudit = new Button("View Full Audit Log →");
        viewAllAudit.getStyleClass().addAll("btn-secondary", "btn-sm");
        viewAllAudit.setOnAction(e -> navigator.navigateTo(MainShellView.ROUTE_AUDIT_LOGS));
        activityHeader.getChildren().addAll(actTitle, sp, viewAllAudit);

        VBox activityCard = new VBox(12, activityHeader, auditTable);
        activityCard.getStyleClass().add("card");

        container.getChildren().addAll(topRow, kpiRow1, kpiRow2, chartsRow1, chartsRow2, participationCard, activityCard);
    }

    private BarChart<String, Number> createBarChart(String title, String xLabel, String yLabel, Map<String, Long> data) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel(xLabel);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yLabel);
        yAxis.setMinorTickVisible(false);

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(false);
        chart.setAnimated(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        if (data == null || data.isEmpty()) {
            series.getData().add(new XYChart.Data<>("None", 0));
        } else {
            data.forEach((k, v) -> {
                String shortKey = k.length() > 20 ? k.substring(0, 18) + "…" : k;
                series.getData().add(new XYChart.Data<>(shortKey, v));
            });
        }
        chart.getData().add(series);
        return chart;
    }

    private VBox wrapChartCard(Node chart) {
        VBox card = new VBox(chart);
        card.getStyleClass().add("card");
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }
}
