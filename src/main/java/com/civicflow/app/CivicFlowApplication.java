package com.civicflow.app;

import com.civicflow.controller.LoginView;
import com.civicflow.controller.MainShellView;
import com.civicflow.exception.GlobalExceptionHandler;
import com.civicflow.security.UserSession;
import com.civicflow.ui.AlertHelper;
import com.civicflow.ui.AsyncTaskRunner;
import com.civicflow.ui.CivicFlowLogo;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Primary JavaFX 21 Desktop Application entry point for CIVICFLOW — Election Management Suite (CEMS).
 */
public class CivicFlowApplication extends Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(CivicFlowApplication.class);

    private ApplicationContext context;
    private Stage primaryStage;
    private String stylesheetUrl;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.stylesheetUrl = getClass().getResource("/css/civicflow.css") != null
                ? getClass().getResource("/css/civicflow.css").toExternalForm()
                : null;

        primaryStage.setTitle("CIVICFLOW — Election Management Suite (CEMS)");
        primaryStage.getIcons().add(CivicFlowLogo.createAppIcon());
        primaryStage.setMinWidth(1080);
        primaryStage.setMinHeight(680);

        SplashScreen splash = new SplashScreen();
        splash.show();

        AsyncTaskRunner.run(
                () -> {
                    Platform.runLater(() -> splash.updateStatus("Connecting to MySQL civicflow_db...", 0.45));
                    ApplicationContext ctx = ApplicationContext.createDefault();
                    if (!ctx.getDatabaseManager().testConnection()) {
                        ctx.close();
                        throw new IllegalStateException("MySQL connection validation failed.");
                    }
                    Platform.runLater(() -> splash.updateStatus("Loading CIVICFLOW workspace...", 0.90));
                    Thread.sleep(350);
                    return ctx;
                },
                ctx -> {
                    this.context = ctx;
                    splash.close();
                    showLoginScreen();
                    primaryStage.show();
                },
                errMsg -> {
                    splash.close();
                    LOGGER.error("Startup failed: {}", errMsg);
                    AlertHelper.showError(
                            "Database Connection Required",
                            "CIVICFLOW could not connect to the MySQL database.\n\n"
                                    + "Details: " + errMsg + "\n\n"
                                    + "Please configure CIVICFLOW_DB_URL, CIVICFLOW_DB_USER, and CIVICFLOW_DB_PASSWORD "
                                    + "or create config/application.properties from config/application.properties.example, "
                                    + "and initialize database/schema.sql.");
                    Platform.exit();
                },
                null,
                "application startup"
        );
    }

    private void showLoginScreen() {
        LoginView loginView = new LoginView(context, this::showMainShell);
        setSceneRoot(loginView.build(), 1120, 700);
        primaryStage.centerOnScreen();
    }

    private void showMainShell(UserSession session) {
        MainShellView shell = new MainShellView(context, session, this::showLoginScreen);
        setSceneRoot(shell.build(), 1320, 820);
        primaryStage.centerOnScreen();
    }

    private void setSceneRoot(Parent root, double width, double height) {
        if (primaryStage.getScene() == null) {
            Scene scene = new Scene(root, width, height);
            if (stylesheetUrl != null) {
                scene.getStylesheets().add(stylesheetUrl);
            }
            primaryStage.setScene(scene);
        } else {
            primaryStage.getScene().setRoot(root);
            if (!primaryStage.isMaximized()) {
                primaryStage.setWidth(Math.max(primaryStage.getWidth(), width));
                primaryStage.setHeight(Math.max(primaryStage.getHeight(), height));
            }
        }
    }

    @Override
    public void stop() {
        try {
            if (context != null) {
                context.close();
            }
        } catch (Exception e) {
            LOGGER.warn("Error closing context: {}", GlobalExceptionHandler.handleAndGetUserMessage(e, "shutdown"));
        } finally {
            AsyncTaskRunner.shutdown();
        }
    }
}
