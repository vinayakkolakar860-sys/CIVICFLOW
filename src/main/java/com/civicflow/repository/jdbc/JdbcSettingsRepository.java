package com.civicflow.repository.jdbc;

import com.civicflow.config.DatabaseManager;
import com.civicflow.exception.DatabaseException;
import com.civicflow.model.AppSetting;
import com.civicflow.repository.SettingsRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcSettingsRepository implements SettingsRepository {

    private final DatabaseManager db;

    public JdbcSettingsRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public List<AppSetting> findAll() {
        String sql = "SELECT setting_key, setting_value, description, updated_at FROM application_settings ORDER BY setting_key ASC";
        List<AppSetting> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DatabaseException("Failed to fetch application settings.", e);
        }
    }

    @Override
    public Optional<AppSetting> findByKey(String key) {
        String sql = "SELECT setting_key, setting_value, description, updated_at FROM application_settings WHERE setting_key = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Failed to load setting '" + key + "'.", e);
        }
    }

    @Override
    public void upsert(String key, String value, String description) {
        String sql = """
                INSERT INTO application_settings (setting_key, setting_value, description)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value),
                                        description = COALESCE(VALUES(description), description)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.setString(3, description);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Failed to save application setting.", e);
        }
    }

    private AppSetting mapRow(ResultSet rs) throws SQLException {
        AppSetting s = new AppSetting();
        s.setSettingKey(rs.getString("setting_key"));
        s.setSettingValue(rs.getString("setting_value"));
        s.setDescription(rs.getString("description"));
        Timestamp ts = rs.getTimestamp("updated_at");
        s.setUpdatedAt(ts != null ? ts.toLocalDateTime() : null);
        return s;
    }
}
