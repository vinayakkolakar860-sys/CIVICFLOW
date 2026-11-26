package com.civicflow.audit;

import com.civicflow.model.AuditLog;
import com.civicflow.model.AuditResultStatus;
import com.civicflow.model.PageResult;
import com.civicflow.repository.AuditLogRepository;
import com.civicflow.security.AccessControlService;
import com.civicflow.security.SessionManager;
import com.civicflow.security.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Centralized audit trail service.
 * Records security and administrative actions without ever persisting passwords,
 * password hashes, authentication tokens, or ballot choices.
 */
public class AuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final SessionManager sessionManager;
    private final AccessControlService accessControl;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLogRepository,
                        SessionManager sessionManager,
                        AccessControlService accessControl,
                        Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.sessionManager = sessionManager;
        this.accessControl = accessControl;
        this.clock = clock;
    }

    public void record(String module, String action, String nonSensitiveDetails, AuditResultStatus status) {
        UserSession session = sessionManager.getCurrentSession().orElse(null);
        Long userId = session != null ? session.getUserId() : null;
        String username = session != null ? session.getUsername() : "SYSTEM";
        recordForActor(userId, username, module, action, nonSensitiveDetails, status);
    }

    public void recordForActor(Long userId, String username, String module, String action,
                               String nonSensitiveDetails, AuditResultStatus status) {
        try {
            AuditLog entry = new AuditLog();
            entry.setUserId(userId);
            entry.setUsername(truncate(username, 60));
            entry.setModule(truncate(module != null ? module.toUpperCase() : "SYSTEM", 60));
            entry.setAction(truncate(action, 80));
            entry.setTimestamp(LocalDateTime.now(clock));
            entry.setDetails(truncate(nonSensitiveDetails, 500));
            entry.setResultStatus(status != null ? status : AuditResultStatus.SUCCESS);
            auditLogRepository.insert(entry);
        } catch (Exception e) {
            // Audit failure must never crash primary transaction flow or leak sensitive data
            LOGGER.error("Unable to persist audit record [{}/{}]: {}", module, action, e.getMessage());
        }
    }

    public PageResult<AuditLog> searchLogs(String module, String searchText, LocalDate fromDate, LocalDate toDate,
                                           int pageNumber, int pageSize) {
        accessControl.requireAdmin();
        return auditLogRepository.search(module, searchText, fromDate, toDate, pageNumber, pageSize);
    }

    public List<AuditLog> exportLogs(String module, String searchText, LocalDate fromDate, LocalDate toDate, int limit) {
        accessControl.requireAdmin();
        return auditLogRepository.findAllFiltered(module, searchText, fromDate, toDate, limit);
    }

    public List<AuditLog> getRecentActivity(int limit) {
        accessControl.requireAdmin();
        return auditLogRepository.findRecent(limit);
    }

    private static String truncate(String val, int max) {
        if (val == null) return null;
        return val.length() <= max ? val : val.substring(0, max);
    }
}
