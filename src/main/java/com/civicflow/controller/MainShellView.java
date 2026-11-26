package com.civicflow.controller;

import com.civicflow.app.ApplicationContext;
import com.civicflow.model.Election;
import com.civicflow.model.RoleType;
import com.civicflow.navigation.ViewNavigator;
import com.civicflow.security.UserSession;
import com.civicflow.ui.AlertHelper;
import com.civicflow.ui.CivicFlowLogo;
import com.civicflow.ui.UiComponents;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Primary desktop application shell with dark enterprise sidebar, top header bar,
 * role-based navigation, dynamic content host, loading overlay, and branded footer.
 */
public class MainShellView implements ViewNavigator {

    public static final String ROUTE_ADMIN_DASHBOARD = "admin_dashboard";
    public static final String ROUTE_VOTER_DASHBOARD = "voter_dashboard";
    public static final String ROUTE_VOTERS = "voters";
    public static final String ROUTE_CANDIDATES = "candidates";
    public static final String ROUTE_PARTIES = "parties";
    public static final String ROUTE_CONSTITUENCIES = "constituencies";
    public static final String ROUTE_ELECTIONS = "elections";
    public static final String ROUTE_RESULTS = "results";
    public static final String ROUTE_REPORTS = "reports";
    public static final String ROUTE_AUDIT_LOGS = "audit_logs";
    public static final String ROUTE_PROFILE = "profile";
    public static final String ROUTE_SETTINGS = "settings";

    private final ApplicationContext ctx;
    private final UserSession session;
    private final Runnable onLogoutCallback;

    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private final Label pageTitleLabel = new Label("Dashboard");
    private final Label pageSubtitleLabel = new Label("Overview & Real-Time Statistics");
    private final StackPane contentContainer = new StackPane();
    private final StackPane loadingOverlay = UiComponents.createLoadingOverlay();

    public MainShellView(ApplicationContext ctx, UserSession session, Runnable onLogoutCallback) {
        this.ctx = ctx;
        this.session = session;
        this.onLogoutCallback = onLogoutCallback;
    }

    public Parent build() {
        BorderPane root = new BorderPane();

        // Sidebar
        VBox sidebar = buildSidebar();
        root.setLeft(sidebar);

        // Top Bar
        HBox topBar = buildTopBar();

        // Center Content + Loading Overlay
        StackPane centerStack = new StackPane(contentContainer, loadingOverlay);
        VBox.setVgrow(centerStack, Priority.ALWAYS);

        // Footer
        HBox footer = buildFooter();

        VBox mainColumn = new VBox(topBar, centerStack, footer);
        VBox.setVgrow(centerStack, Priority.ALWAYS);
        root.setCenter(mainColumn);

        // Load default landing screen based on role
        if (session.getRole().isAdmin()) {
            navigateTo(ROUTE_ADMIN_DASHBOARD);
        } else {
            navigateTo(ROUTE_VOTER_DASHBOARD);
        }

        return root;
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(4);
        sidebar.getStyleClass().add("sidebar");

        // Brand Header
        HBox brandBox = new HBox(12);
        brandBox.setAlignment(Pos.CENTER_LEFT);
        brandBox.setPadding(new Insets(4, 8, 16, 8));
        VBox brandText = new VBox(1);
        Label title = new Label("CIVICFLOW");
        title.getStyleClass().add("sidebar-brand-title");
        Label sub = new Label("Election Suite • CEMS");
        sub.getStyleClass().add("sidebar-brand-sub");
        brandText.getChildren().addAll(title, sub);
        brandBox.getChildren().addAll(CivicFlowLogo.createLogoMark(36), brandText);
        sidebar.getChildren().add(brandBox);

        RoleType role = session.getRole();

        if (role.isAdmin()) {
            sidebar.getChildren().add(sectionLabel("OVERVIEW"));
            addNavButton(sidebar, ROUTE_ADMIN_DASHBOARD, "Dashboard");

            sidebar.getChildren().add(sectionLabel("ELECTION OPERATIONS"));
            addNavButton(sidebar, ROUTE_ELECTIONS, "Elections");
            addNavButton(sidebar, ROUTE_VOTERS, "Voters");
            addNavButton(sidebar, ROUTE_CANDIDATES, "Candidates");
            addNavButton(sidebar, ROUTE_PARTIES, "Fictional Parties");
            addNavButton(sidebar, ROUTE_CONSTITUENCIES, "Constituencies");

            sidebar.getChildren().add(sectionLabel("ANALYTICS & GOVERNANCE"));
            addNavButton(sidebar, ROUTE_RESULTS, "Results Dashboard");
            addNavButton(sidebar, ROUTE_REPORTS, "Reports & Exports");
            addNavButton(sidebar, ROUTE_AUDIT_LOGS, "Audit Logs");

            sidebar.getChildren().add(sectionLabel("ACCOUNT & SECURITY"));
            addNavButton(sidebar, ROUTE_PROFILE, "Change Password");
            if (role == RoleType.SUPER_ADMIN) {
                sidebar.getChildren().add(sectionLabel("SYSTEM"));
                addNavButton(sidebar, ROUTE_SETTINGS, "Settings & Admins");
            }
        } else {
            sidebar.getChildren().add(sectionLabel("VOTER PORTAL"));
            addNavButton(sidebar, ROUTE_VOTER_DASHBOARD, "Voter Dashboard");
            addNavButton(sidebar, ROUTE_RESULTS, "Published Results");
            sidebar.getChildren().add(sectionLabel("ACCOUNT & SECURITY"));
            addNavButton(sidebar, ROUTE_PROFILE, "Change Password");
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button logoutBtn = new Button("Sign Out");
        logoutBtn.getStyleClass().add("nav-button");
        logoutBtn.setStyle("-fx-text-fill: #FCA5A5;");
        logoutBtn.setOnAction(e -> handleLogout());

        sidebar.getChildren().addAll(spacer, logoutBtn);
        return sidebar;
    }

    private HBox buildTopBar() {
        HBox topBar = new HBox(16);
        topBar.getStyleClass().add("topbar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        pageTitleLabel.getStyleClass().add("page-title");
        pageSubtitleLabel.getStyleClass().add("page-subtitle");
        VBox titleBox = new VBox(2, pageTitleLabel, pageSubtitleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label roleBadge = UiComponents.createStatusBadge(session.getRole().name());
        Label userNameLabel = new Label(session.getFullName() + " (" + session.getUsername() + ")");
        userNameLabel.setStyle("-fx-font-weight: 600; -fx-text-fill: #182230; -fx-font-size: 13px;");

        Button changePassBtn = new Button("Change Password");
        changePassBtn.getStyleClass().addAll("btn-secondary", "btn-sm");
        changePassBtn.setOnAction(e -> navigateTo(ROUTE_PROFILE));

        Button logoutBtn = new Button("Logout");
        logoutBtn.getStyleClass().addAll("btn-danger", "btn-sm");
        logoutBtn.setOnAction(e -> handleLogout());

        HBox userControls = new HBox(10, roleBadge, userNameLabel, changePassBtn, logoutBtn);
        userControls.setAlignment(Pos.CENTER_RIGHT);

        topBar.getChildren().addAll(titleBox, spacer, userControls);
        return topBar;
    }

    private HBox buildFooter() {
        HBox footer = new HBox(12);
        footer.getStyleClass().add("app-footer");
        footer.setAlignment(Pos.CENTER_LEFT);

        Label left = new Label("CIVICFLOW Election Management Suite");
        left.getStyleClass().add("footer-text");
        left.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label right = new Label("Educational Election Simulation Platform • Fictional Demo Data Only • v" + ctx.getAppConfig().getAppVersion());
        right.getStyleClass().add("footer-text");

        footer.getChildren().addAll(left, spacer, right);
        return footer;
    }

    private Label sectionLabel(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("sidebar-section-label");
        return lbl;
    }

    private void addNavButton(VBox sidebar, String routeKey, String label) {
        Button btn = new Button(label);
        btn.getStyleClass().add("nav-button");
        btn.setOnAction(e -> navigateTo(routeKey));
        navButtons.put(routeKey, btn);
        sidebar.getChildren().add(btn);
    }

    private void setActiveNav(String routeKey) {
        navButtons.forEach((key, btn) -> {
            btn.getStyleClass().remove("nav-button-active");
            if (key.equals(routeKey)) {
                btn.getStyleClass().add("nav-button-active");
            }
        });
    }

    public void setLoading(boolean visible) {
        loadingOverlay.setVisible(visible);
    }

    @Override
    public void navigateTo(String routeKey) {
        RoleType role = session.getRole();

        // Defense-in-depth UI route guard (service layer enforces RBAC independently)
        if (role == RoleType.VOTER && !routeKey.equals(ROUTE_VOTER_DASHBOARD)
                && !routeKey.equals(ROUTE_RESULTS) && !routeKey.equals(ROUTE_PROFILE)) {
            AlertHelper.showError("Access Restricted", "Voters cannot access administrative modules.");
            return;
        }
        if (routeKey.equals(ROUTE_SETTINGS) && role != RoleType.SUPER_ADMIN) {
            AlertHelper.showError("Access Restricted", "Only Super Administrators may access Settings & Admin Accounts.");
            return;
        }

        setActiveNav(routeKey);
        Node viewNode;
        switch (routeKey) {
            case ROUTE_ADMIN_DASHBOARD -> {
                setHeader("Admin Dashboard", "Live database metrics, visualizations, and recent governance activity");
                viewNode = new AdminDashboardView(ctx, this, this::setLoading).build();
            }
            case ROUTE_VOTER_DASHBOARD -> {
                setHeader("Voter Dashboard", "View eligible simulated elections, participation status, and published results");
                viewNode = new VoterDashboardView(ctx, this, this::setLoading).build();
            }
            case ROUTE_VOTERS -> {
                setHeader("Voter Management", "Register, verify, search, and manage fictional voter accounts");
                viewNode = new VoterManagementView(ctx, this::setLoading).build();
            }
            case ROUTE_CANDIDATES -> {
                setHeader("Candidate Management", "Manage fictional candidates, constituency assignments, and approvals");
                viewNode = new CandidateManagementView(ctx, this::setLoading).build();
            }
            case ROUTE_PARTIES -> {
                setHeader("Fictional Party Management", "Administer demonstration political parties and optional emblems");
                viewNode = new PartyManagementView(ctx, this::setLoading).build();
            }
            case ROUTE_CONSTITUENCIES -> {
                setHeader("Constituency Management", "Configure fictional districts and inspect election history");
                viewNode = new ConstituencyManagementView(ctx, this::setLoading).build();
            }
            case ROUTE_ELECTIONS -> {
                setHeader("Election Management", "Create, schedule, open, close, and publish simulated elections");
                viewNode = new ElectionManagementView(ctx, this, this::setLoading).build();
            }
            case ROUTE_RESULTS -> {
                setHeader("Election Results Dashboard", "Aggregated simulated vote totals, charts, and constituency breakdowns");
                viewNode = new ResultsDashboardView(ctx, null, this::setLoading).build();
            }
            case ROUTE_REPORTS -> {
                setHeader("Reports & Exports", "Generate filtered administrative reports and export securely to PDF or CSV");
                viewNode = new ReportsView(ctx, this::setLoading).build();
            }
            case ROUTE_AUDIT_LOGS -> {
                setHeader("Audit Log Viewer", "Immutable security and governance activity trail");
                viewNode = new AuditLogView(ctx, this::setLoading).build();
            }
            case ROUTE_PROFILE -> {
                setHeader("My Profile & Security", "View account details and update your password");
                viewNode = new UserProfileView(ctx, this::setLoading).build();
            }
            case ROUTE_SETTINGS -> {
                setHeader("System Settings & Administrators", "Manage administrator accounts and security policy parameters");
                viewNode = new SettingsView(ctx, this::setLoading).build();
            }
            default -> {
                setHeader("Dashboard", "CIVICFLOW");
                viewNode = UiComponents.createEmptyState("Screen not found", "Select a module from the sidebar.");
            }
        }

        ScrollPane scroll = new ScrollPane(viewNode);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setPadding(new Insets(20, 24, 24, 24));
        contentContainer.getChildren().setAll(scroll);
    }

    @Override
    public void openVotingBooth(Election election) {
        if (session.getRole() != RoleType.VOTER) {
            AlertHelper.showError("Access Restricted", "Administrators cannot enter the voter ballot booth.");
            return;
        }
        setActiveNav(ROUTE_VOTER_DASHBOARD);
        setHeader("Simulated Voting Booth", election.getElectionName() + " (" + election.getElectionCode() + ")");
        Node view = new VotingBoothView(ctx, election, this, this::setLoading).build();
        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.setPadding(new Insets(20, 24, 24, 24));
        contentContainer.getChildren().setAll(scroll);
    }

    @Override
    public void openResultsForElection(long electionId) {
        setActiveNav(ROUTE_RESULTS);
        setHeader("Election Results Dashboard", "Aggregated simulated vote totals, charts, and constituency breakdowns");
        Node view = new ResultsDashboardView(ctx, electionId, this::setLoading).build();
        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.setPadding(new Insets(20, 24, 24, 24));
        contentContainer.getChildren().setAll(scroll);
    }

    @Override
    public void handleLogout() {
        if (AlertHelper.confirm("Sign Out", "Confirm Sign Out", "Are you sure you want to end your current CIVICFLOW session?")) {
            ctx.getAuthService().logout();
            onLogoutCallback.run();
        }
    }

    private void setHeader(String title, String subtitle) {
        pageTitleLabel.setText(title);
        pageSubtitleLabel.setText(subtitle);
    }
}
