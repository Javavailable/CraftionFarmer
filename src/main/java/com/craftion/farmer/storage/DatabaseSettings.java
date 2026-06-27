package com.craftion.farmer.storage;

import com.craftion.farmer.config.ConfigManager;

public record DatabaseSettings(
    DatabaseType type,
    String sqliteFile,
    String mysqlHost,
    int mysqlPort,
    String mysqlDatabase,
    String mysqlUsername,
    String mysqlPassword,
    int mysqlPoolSize
) {

    private static final String DEFAULT_SQLITE_FILE = "craftionfarmer.db";

    public static DatabaseSettings from(ConfigManager configManager) {
        return new DatabaseSettings(
            DatabaseType.from(configManager.databaseType()),
            valueOrDefault(configManager.sqliteFile(), DEFAULT_SQLITE_FILE),
            valueOrDefault(configManager.mysqlHost(), "localhost"),
            configManager.mysqlPort(),
            valueOrDefault(configManager.mysqlDatabase(), "craftionfarmer"),
            valueOrDefault(configManager.mysqlUsername(), "root"),
            configManager.mysqlPassword(),
            Math.max(1, configManager.mysqlPoolSize())
        );
    }

    private static String valueOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
