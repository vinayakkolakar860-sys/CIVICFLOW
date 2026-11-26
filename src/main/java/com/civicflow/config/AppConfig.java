package com.civicflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Centralized application configuration loader.
 * Resolves settings from environment variables first, then external config/application.properties.
 * Never embeds hardcoded production database credentials in source code.
 */
public class AppConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppConfig.class);

    public static final String ENV_DB_URL = "CIVICFLOW_DB_URL";
    public static final String ENV_DB_USER = "CIVICFLOW_DB_USER";
    public static final String ENV_DB_PASSWORD = "CIVICFLOW_DB_PASSWORD";

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final int maxPoolSize;
    private final int minIdle;
    private final long connectionTimeoutMs;
    private final String appName;
    private final String appVersion;
    private final boolean demoMode;
    private final Path uploadsDir;
    private final Path exportsDir;

    public AppConfig(String dbUrl, String dbUser, String dbPassword,
                     int maxPoolSize, int minIdle, long connectionTimeoutMs,
                     String appName, String appVersion, boolean demoMode,
                     Path uploadsDir, Path exportsDir) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.maxPoolSize = maxPoolSize;
        this.minIdle = minIdle;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.appName = appName;
        this.appVersion = appVersion;
        this.demoMode = demoMode;
        this.uploadsDir = uploadsDir;
        this.exportsDir = exportsDir;
    }

    public static AppConfig load() {
        Properties props = new Properties();
        Path externalConfig = resolveConfigFile();
        if (externalConfig != null && Files.exists(externalConfig)) {
            try (InputStream in = Files.newInputStream(externalConfig)) {
                props.load(in);
                LOGGER.info("Loaded configuration from {}", externalConfig.toAbsolutePath());
            } catch (IOException e) {
                LOGGER.warn("Unable to read configuration file {}: {}", externalConfig, e.getMessage());
            }
        } else {
            LOGGER.info("No external config/application.properties found; relying on environment variables.");
        }

        String url = getEnvOrProp(ENV_DB_URL, props, "db.url",
                "jdbc:mysql://localhost:3306/civicflow_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        String user = getEnvOrProp(ENV_DB_USER, props, "db.user", "");
        String password = getEnvOrProp(ENV_DB_PASSWORD, props, "db.password", "");

        int maxPool = parseInt(props.getProperty("db.pool.maxSize"), 10);
        int minIdleVal = parseInt(props.getProperty("db.pool.minIdle"), 2);
        long timeoutMs = parseLong(props.getProperty("db.pool.connectionTimeoutMs"), 10000L);
        String name = props.getProperty("app.name", "CIVICFLOW — Election Management Suite (CEMS)");
        String version = props.getProperty("app.version", "1.0.0");
        boolean demo = Boolean.parseBoolean(props.getProperty("app.demoMode", "true"));
        Path uploads = Paths.get(props.getProperty("app.uploadsDir", "uploads")).toAbsolutePath().normalize();
        Path exports = Paths.get(props.getProperty("app.exportsDir", "exports")).toAbsolutePath().normalize();

        return new AppConfig(url, user, password, maxPool, minIdleVal, timeoutMs, name, version, demo, uploads, exports);
    }

    private static Path resolveConfigFile() {
        String customPath = System.getProperty("civicflow.config");
        if (customPath != null && !customPath.isBlank()) {
            return Paths.get(customPath);
        }
        Path local = Paths.get("config", "application.properties");
        if (Files.exists(local)) {
            return local;
        }
        Path parent = Paths.get("..", "config", "application.properties");
        if (Files.exists(parent)) {
            return parent.normalize();
        }
        return local;
    }

    private static String getEnvOrProp(String envKey, Properties props, String propKey, String defaultValue) {
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }
        String sysProp = System.getProperty(envKey);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }
        return props.getProperty(propKey, defaultValue).trim();
    }

    private static int parseInt(String val, int fallback) {
        try {
            return val != null ? Integer.parseInt(val.trim()) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String val, long fallback) {
        try {
            return val != null ? Long.parseLong(val.trim()) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public String getDbUrl() { return dbUrl; }
    public String getDbUser() { return dbUser; }
    public String getDbPassword() { return dbPassword; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public int getMinIdle() { return minIdle; }
    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public String getAppName() { return appName; }
    public String getAppVersion() { return appVersion; }
    public boolean isDemoMode() { return demoMode; }
    public Path getUploadsDir() { return uploadsDir; }
    public Path getExportsDir() { return exportsDir; }
}
