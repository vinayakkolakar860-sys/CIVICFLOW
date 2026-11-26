package com.civicflow.exception;

/**
 * Thrown when an HTTP request payload exceeds the allowed limit (e.g. 4 MiB),
 * resulting in an HTTP 413 Payload Too Large response.
 */
public class PayloadTooLargeException extends CivicFlowException {

    public PayloadTooLargeException(String userMessage) {
        super("PAYLOAD_TOO_LARGE", userMessage);
    }

    public PayloadTooLargeException(String userMessage, Throwable cause) {
        super("PAYLOAD_TOO_LARGE", userMessage, cause);
    }
}
