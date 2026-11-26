package com.civicflow.model;

import java.time.LocalDateTime;

/**
 * Represents an immutable audit trail entry for security and governance visibility.
 */
public class AuditLog {
    private long auditId;
    private Long userId;
    private String username;
    private String action;
    private String module;
    private LocalDateTime timestamp;
    private String details;
    private AuditResultStatus resultStatus;

    public long getAuditId() { return auditId; }
    public void setAuditId(long auditId) { this.auditId = auditId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public AuditResultStatus getResultStatus() { return resultStatus; }
    public void setResultStatus(AuditResultStatus resultStatus) { this.resultStatus = resultStatus; }
}
