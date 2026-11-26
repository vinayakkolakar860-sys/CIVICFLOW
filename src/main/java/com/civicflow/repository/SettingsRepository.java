package com.civicflow.repository;

import com.civicflow.model.AppSetting;

import java.util.List;
import java.util.Optional;

public interface SettingsRepository {
    List<AppSetting> findAll();
    Optional<AppSetting> findByKey(String key);
    void upsert(String key, String value, String description);
}
