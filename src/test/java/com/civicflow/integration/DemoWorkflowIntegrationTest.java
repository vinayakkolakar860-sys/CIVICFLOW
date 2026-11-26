package com.civicflow.integration;

import com.civicflow.app.ApplicationContext;
import com.civicflow.config.AppConfig;
import com.civicflow.exception.AuthorizationException;
import com.civicflow.model.DashboardStats;
import com.civicflow.model.RoleType;
import com.civicflow.reporting.ReportData;
import com.civicflow.reporting.ReportType;
import com.civicflow.security.UserSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests against the seeded demonstration dataset in civicflow_db:
 * - Verifies demo Super Admin, Election Admin, and Voter authentication with real BCrypt hashes
 * - Verifies DashboardService returns real database statistics
 * - Verifies RBAC blocks Election Admin from Super Admin settings and blocks Voter from Admin dashboard
 * - Verifies all 11 reports generate cleanly and export to PDF and CSV
 */
class DemoWorkflowIntegrationTest {

    private static ApplicationContext ctx;
    private static boolean demoSeeded = false;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setUp() {
        AppConfig cfg = AppConfig.load();
        if (cfg.getDbUser() == null || cfg.getDbUser().isBlank()) return;
        try {
            ctx = new ApplicationContext(cfg, Clock.systemDefaultZone());
            if (ctx.getDatabaseManager().testConnection()) {
                UserSession s = ctx.getAuthService().login("superadmin", "Abc123");
                demoSeeded = (s != null);
                ctx.getAuthService().logout();
            }
        } catch (Exception e) {
            demoSeeded = false;
        }
    }

    @AfterAll
    static void tearDown() {
        if (ctx != null) ctx.close();
    }

    @Test
    void demoAccountsAuthenticateWithCorrectRolesAndMustChangePasswordFlag() {
        Assumptions.assumeTrue(demoSeeded, "Seeded civicflow_db demo dataset not present");

        UserSession superSess = ctx.getAuthService().login("superadmin", "Abc123");
        assertEquals(RoleType.SUPER_ADMIN, superSess.getRole());
        assertFalse(superSess.isMustChangePassword());
        ctx.getAuthService().logout();

        UserSession elSess = ctx.getAuthService().login("electionadmin", "Abc123");
        assertEquals(RoleType.ELECTION_ADMIN, elSess.getRole());
        assertFalse(elSess.isMustChangePassword());
        // Election admin cannot modify Super Admin settings
        assertThrows(AuthorizationException.class, () -> ctx.getUserManagementService().getAllAdminAccounts());
        ctx.getAuthService().logout();

        UserSession voterSess = ctx.getAuthService().login("voter.maya", "Abc123");
        assertEquals(RoleType.VOTER, voterSess.getRole());
        assertNotNull(voterSess.getVoterProfile());
        assertThrows(AuthorizationException.class, () -> ctx.getDashboardService().loadAdminDashboardStats());
        ctx.getAuthService().logout();
    }

    @Test
    void adminDashboardAndAllElevenReportsExecuteAgainstLiveDatabase() throws Exception {
        Assumptions.assumeTrue(demoSeeded, "Seeded civicflow_db demo dataset not present");

        ctx.getAuthService().login("superadmin", "Abc123");
        try {
            DashboardStats stats = ctx.getDashboardService().loadAdminDashboardStats();
            assertTrue(stats.getTotalVoters() >= 7);
            assertTrue(stats.getVerifiedVoters() >= 5);
            assertTrue(stats.getPendingVoters() >= 2);
            assertTrue(stats.getTotalCandidates() >= 8);
            assertTrue(stats.getTotalConstituencies() >= 4);
            assertTrue(stats.getTotalElections() >= 4);
            assertTrue(stats.getOpenElections() >= 1);
            assertTrue(stats.getPublishedResults() >= 1);

            for (ReportType rt : ReportType.values()) {
                ReportData data = ctx.getReportService().generateReport(rt, null, null, null, null, null);
                assertNotNull(data);
                assertFalse(data.getHeaders().isEmpty(), "Report " + rt + " must have headers");

                Path pdf = tempDir.resolve(rt.name().toLowerCase() + ".pdf");
                Path csv = tempDir.resolve(rt.name().toLowerCase() + ".csv");
                ctx.getReportService().exportPdf(data, pdf);
                ctx.getReportService().exportCsv(data, csv);
                assertTrue(Files.exists(pdf) && Files.size(pdf) > 300, "PDF must be created for " + rt);
                assertTrue(Files.exists(csv) && Files.size(csv) > 50, "CSV must be created for " + rt);
            }
        } finally {
            ctx.getAuthService().logout();
        }
    }
}
