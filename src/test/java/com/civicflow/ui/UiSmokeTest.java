package com.civicflow.ui;

import com.civicflow.app.ApplicationContext;
import com.civicflow.config.AppConfig;
import com.civicflow.controller.LoginView;
import com.civicflow.controller.MainShellView;
import com.civicflow.security.UserSession;
import javafx.application.Platform;
import javafx.scene.Parent;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated JavaFX UI smoke test verifying:
 * - Logo and AppIcon rendering
 * - LoginView construction
 * - Role-based MainShellView construction for Admin and Voter sessions
 * - Clean logout state reset
 */
class UiSmokeTest {

    private static boolean fxInitialized = false;
    private static ApplicationContext ctx;
    private static boolean dbReady = false;

    @BeforeAll
    static void initFxAndContext() {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            fxInitialized = latch.await(5, TimeUnit.SECONDS);
        } catch (IllegalStateException alreadyStarted) {
            fxInitialized = true;
        } catch (Throwable t) {
            fxInitialized = false;
        }

        AppConfig cfg = AppConfig.load();
        if (cfg.getDbUser() != null && !cfg.getDbUser().isBlank()) {
            try {
                ctx = new ApplicationContext(cfg, Clock.systemDefaultZone());
                dbReady = ctx.getDatabaseManager().testConnection();
            } catch (Exception ignored) {
                dbReady = false;
            }
        }
    }

    @AfterAll
    static void tearDown() {
        if (ctx != null) ctx.close();
    }

    @Test
    void brandingLogoAndAppIconRenderCleanly() throws Exception {
        Assumptions.assumeTrue(fxInitialized, "JavaFX toolkit unavailable in headless environment");
        runOnFxThread(() -> {
            assertNotNull(CivicFlowLogo.createLogoMark(48));
            assertNotNull(CivicFlowLogo.createAppIcon());
            assertNotNull(UiComponents.createStatCard("Test KPI", "42", "#2855D9"));
            assertNotNull(UiComponents.createStatusBadge("OPEN"));
        });
    }

    @Test
    void loginViewAndRoleBasedDashboardsBuildWithoutErrors() throws Exception {
        Assumptions.assumeTrue(fxInitialized && dbReady, "JavaFX toolkit or MySQL DB unavailable");

        runOnFxThread(() -> {
            LoginView loginView = new LoginView(ctx, s -> {});
            Parent loginRoot = loginView.build();
            assertNotNull(loginRoot);

            // Admin Dashboard Shell
            UserSession adminSession = ctx.getAuthService().login("superadmin", "Abc123");
            MainShellView adminShell = new MainShellView(ctx, adminSession, () -> {});
            assertNotNull(adminShell.build());
            adminShell.navigateTo(MainShellView.ROUTE_VOTERS);
            adminShell.navigateTo(MainShellView.ROUTE_ELECTIONS);
            adminShell.navigateTo(MainShellView.ROUTE_REPORTS);
            ctx.getAuthService().logout();
            assertFalse(ctx.getSessionManager().isAuthenticated());

            // Voter Dashboard Shell
            UserSession voterSession = ctx.getAuthService().login("voter.maya", "Abc123");
            MainShellView voterShell = new MainShellView(ctx, voterSession, () -> {});
            assertNotNull(voterShell.build());
            voterShell.navigateTo(MainShellView.ROUTE_RESULTS);
            voterShell.navigateTo(MainShellView.ROUTE_PROFILE);
            ctx.getAuthService().logout();
            assertFalse(ctx.getSessionManager().isAuthenticated());
        });
    }

    private void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS), "FX thread task timed out");
        if (error.get() != null) {
            fail("JavaFX UI error: " + error.get().getMessage(), error.get());
        }
    }
}
