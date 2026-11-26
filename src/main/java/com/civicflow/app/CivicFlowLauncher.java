package com.civicflow.app;

import javafx.application.Application;

/**
 * Non-Application main launcher class.
 * Required to launch JavaFX 21 cleanly from classpath and shaded JARs without module-path issues.
 */
public final class CivicFlowLauncher {

    private CivicFlowLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(CivicFlowApplication.class, args);
    }
}
