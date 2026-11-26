package com.civicflow.service;

import com.civicflow.audit.AuditService;
import com.civicflow.exception.ValidationException;
import com.civicflow.model.AppSetting;
import com.civicflow.model.AuditResultStatus;
import com.civicflow.repository.SettingsRepository;
import com.civicflow.security.AccessControlService;

import java.util.List;

/**
 * Manages configurable application settings stored in MySQL `application_settings`.
 */
public class SettingsService {

    public static final String KEY_MAX_FAILED_ATTEMPTS = "auth.max_failed_attempts";
    public static final String KEY_LOCKOUT_MINUTES = "auth.lockout_minutes";
    public static final String KEY_PAGE_SIZE = "ui.page_size";
    public static final String KEY_DEMO_BANNER = "app.demo_banner";
    public static final String KEY_MIN_VOTER_AGE = "voting.min_voter_age";

    private final SettingsRepository settingsRepository;
    private final AccessControlService accessControl;
    private final AuditService auditService;

    public SettingsService(SettingsRepository settingsRepository,
                           AccessControlService accessControl,
                           AuditService auditService) {
        this.settingsRepository = settingsRepository;
        this.accessControl = accessControl;
        this.auditService = auditService;
    }

    public List<AppSetting> getAllSettings() {
        accessControl.requireAdmin();
        return settingsRepository.findAll();
    }

    public int getIntSetting(String key, int defaultValue) {
        return settingsRepository.findByKey(key)
                .map(AppSetting::getSettingValue)
                .map(val -> {
                    try {
                        return Integer.parseInt(val.trim());
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    public String getStringSetting(String key, String defaultValue) {
        return settingsRepository.findByKey(key)
                .map(AppSetting::getSettingValue)
                .filter(v -> !v.isBlank())
                .orElse(defaultValue);
    }

    public void updateSetting(String key, String value, String description) {
        accessControl.requireSuperAdmin();
        if (key == null || key.isBlank()) {
            throw new ValidationException("Setting key is required.");
        }
        if (value == null || value.isBlank()) {
            throw new ValidationException("Setting value cannot be empty.");
        }
        if (KEY_MAX_FAILED_ATTEMPTS.equals(key) || KEY_LOCKOUT_MINUTES.equals(key)
                || KEY_PAGE_SIZE.equals(key) || KEY_MIN_VOTER_AGE.equals(key)) {
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed < 1 || parsed > 1000) {
                    throw new ValidationException("Numeric setting '" + key + "' must be between 1 and 1000.");
                }
            } catch (NumberFormatException e) {
                throw new ValidationException("Setting '" + key + "' must be a valid integer.");
            }
        }
        settingsRepository.upsert(key.trim(), value.trim(), description);
        auditService.record("SETTINGS", "UPDATE_SETTING",
                "Updated setting " + key + " = " + value.trim(), AuditResultStatus.SUCCESS);
    }
}
