package com.civicflow.exception;

public class ExportException extends CivicFlowException {
    public ExportException(String userMessage) {
        super("EXPORT_ERROR", userMessage);
    }

    public ExportException(String userMessage, Throwable cause) {
        super("EXPORT_ERROR", userMessage, cause);
    }
}
