package com.craftion.farmer.storage;

import java.util.Locale;

public enum DatabaseType {
    SQLITE,
    MYSQL;

    public static DatabaseType from(String value) {
        if (value == null || value.isBlank()) {
            return SQLITE;
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return SQLITE;
        }
    }
}
