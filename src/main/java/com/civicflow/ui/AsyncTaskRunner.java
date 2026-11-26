package com.civicflow.ui;

import com.civicflow.exception.GlobalExceptionHandler;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Executes database and export tasks asynchronously off the JavaFX Application Thread
 * using managed daemon threads so the desktop UI remains responsive without thread leaks.
 */
public final class AsyncTaskRunner {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "civicflow-worker");
        t.setDaemon(true);
        return t;
    });

    private AsyncTaskRunner() {
    }

    public static <T> void run(Callable<T> work,
                               Consumer<T> onSuccess,
                               Consumer<String> onError,
                               Consumer<Boolean> loadingToggle,
                               String operationName) {
        if (loadingToggle != null) {
            Platform.runLater(() -> loadingToggle.accept(true));
        }

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };

        task.setOnSucceeded(e -> {
            if (loadingToggle != null) loadingToggle.accept(false);
            if (onSuccess != null) onSuccess.accept(task.getValue());
        });

        task.setOnFailed(e -> {
            if (loadingToggle != null) loadingToggle.accept(false);
            String msg = GlobalExceptionHandler.handleAndGetUserMessage(task.getException(), operationName);
            if (onError != null) {
                onError.accept(msg);
            } else {
                AlertHelper.showError("Operation Failed", msg);
            }
        });

        EXECUTOR.submit(task);
    }

    public static void shutdown() {
        EXECUTOR.shutdownNow();
    }
}
