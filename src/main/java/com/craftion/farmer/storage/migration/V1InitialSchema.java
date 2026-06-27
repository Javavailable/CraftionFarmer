package com.craftion.farmer.storage.migration;

import com.craftion.farmer.storage.DatabaseType;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class V1InitialSchema implements Migration {

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String name() {
        return "initial_schema";
    }

    @Override
    public void migrate(Connection connection, DatabaseType databaseType) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements(databaseType)) {
                statement.executeUpdate(sql);
            }
        }
    }

    private List<String> statements(DatabaseType databaseType) {
        if (databaseType == DatabaseType.MYSQL) {
            return mysqlStatements();
        }
        return sqliteStatements();
    }

    private List<String> sqliteStatements() {
        return List.of(
            "CREATE TABLE IF NOT EXISTS farmers ("
                + "id TEXT PRIMARY KEY, "
                + "region_id TEXT NOT NULL, "
                + "owner_uuid TEXT NOT NULL, "
                + "world TEXT NOT NULL, "
                + "x REAL NOT NULL, "
                + "y REAL NOT NULL, "
                + "z REAL NOT NULL, "
                + "yaw REAL NOT NULL, "
                + "pitch REAL NOT NULL, "
                + "level INTEGER NOT NULL DEFAULT 1, "
                + "collecting_enabled INTEGER NOT NULL DEFAULT 0, "
                + "created_at BIGINT NOT NULL, "
                + "updated_at BIGINT NOT NULL"
                + ")",
            "CREATE TABLE IF NOT EXISTS farmer_members ("
                + "farmer_id TEXT NOT NULL, "
                + "player_uuid TEXT NOT NULL, "
                + "role TEXT NOT NULL, "
                + "added_at BIGINT NOT NULL, "
                + "PRIMARY KEY (farmer_id, player_uuid), "
                + "FOREIGN KEY (farmer_id) REFERENCES farmers(id) ON DELETE CASCADE"
                + ")",
            "CREATE TABLE IF NOT EXISTS farmer_storage ("
                + "farmer_id TEXT NOT NULL, "
                + "material_key TEXT NOT NULL, "
                + "amount BIGINT NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (farmer_id, material_key), "
                + "FOREIGN KEY (farmer_id) REFERENCES farmers(id) ON DELETE CASCADE"
                + ")",
            "CREATE TABLE IF NOT EXISTS farmer_settings ("
                + "farmer_id TEXT NOT NULL, "
                + "setting_key TEXT NOT NULL, "
                + "setting_value TEXT NOT NULL, "
                + "PRIMARY KEY (farmer_id, setting_key), "
                + "FOREIGN KEY (farmer_id) REFERENCES farmers(id) ON DELETE CASCADE"
                + ")",
            "CREATE TABLE IF NOT EXISTS farmer_modules ("
                + "farmer_id TEXT NOT NULL, "
                + "module_key TEXT NOT NULL, "
                + "enabled INTEGER NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (farmer_id, module_key), "
                + "FOREIGN KEY (farmer_id) REFERENCES farmers(id) ON DELETE CASCADE"
                + ")",
            "CREATE TABLE IF NOT EXISTS farmer_logs ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "farmer_id TEXT NOT NULL, "
                + "actor_uuid TEXT NULL, "
                + "action TEXT NOT NULL, "
                + "detail TEXT NULL, "
                + "created_at BIGINT NOT NULL, "
                + "FOREIGN KEY (farmer_id) REFERENCES farmers(id) ON DELETE CASCADE"
                + ")"
        );
    }

    private List<String> mysqlStatements() {
        String tableOptions = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        return List.of(
            "CREATE TABLE IF NOT EXISTS farmers ("
                + "id VARCHAR(36) PRIMARY KEY, "
                + "region_id VARCHAR(128) NOT NULL, "
                + "owner_uuid VARCHAR(36) NOT NULL, "
                + "world VARCHAR(128) NOT NULL, "
                + "x DOUBLE NOT NULL, "
                + "y DOUBLE NOT NULL, "
                + "z DOUBLE NOT NULL, "
                + "yaw FLOAT NOT NULL, "
                + "pitch FLOAT NOT NULL, "
                + "level INT NOT NULL DEFAULT 1, "
                + "collecting_enabled BOOLEAN NOT NULL DEFAULT FALSE, "
                + "created_at BIGINT NOT NULL, "
                + "updated_at BIGINT NOT NULL"
                + ")" + tableOptions,
            "CREATE TABLE IF NOT EXISTS farmer_members ("
                + "farmer_id VARCHAR(36) NOT NULL, "
                + "player_uuid VARCHAR(36) NOT NULL, "
                + "role VARCHAR(32) NOT NULL, "
                + "added_at BIGINT NOT NULL, "
                + "PRIMARY KEY (farmer_id, player_uuid), "
                + "CONSTRAINT fk_farmer_members_farmer FOREIGN KEY (farmer_id) REFERENCES farmers(id) ON DELETE CASCADE"
                + ")" + tableOptions,
            "CREATE TABLE IF NOT EXISTS farmer_storage ("
                + "farmer_id VARCHAR(36) NOT NULL, "
                + "material_key VARCHAR(128) NOT NULL, "
                + "amount BIGINT NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (farmer_id, material_key), "
                + "CONSTRAINT fk_farmer_storage_farmer FOREIGN KEY (farmer_id) REFERENCES farmers(id) ON DELETE CASCADE"
                + ")" + tableOptions,
            "CREATE TABLE IF NOT EXISTS farmer_settings ("
                + "farmer_id VARCHAR(36) NOT NULL, "
                + "setting_key VARCHAR(128) NOT NULL, "
                + "setting_value TEXT NOT NULL, "
                + "PRIMARY KEY (farmer_id, setting_key), "
                + "CONSTRAINT fk_farmer_settings_farmer FOREIGN KEY (farmer_id) REFERENCES farmers(id) ON DELETE CASCADE"
                + ")" + tableOptions,
            "CREATE TABLE IF NOT EXISTS farmer_modules ("
                + "farmer_id VARCHAR(36) NOT NULL, "
                + "module_key VARCHAR(128) NOT NULL, "
                + "enabled BOOLEAN NOT NULL DEFAULT FALSE, "
                + "PRIMARY KEY (farmer_id, module_key), "
                + "CONSTRAINT fk_farmer_modules_farmer FOREIGN KEY (farmer_id) REFERENCES farmers(id) ON DELETE CASCADE"
                + ")" + tableOptions,
            "CREATE TABLE IF NOT EXISTS farmer_logs ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "farmer_id VARCHAR(36) NOT NULL, "
                + "actor_uuid VARCHAR(36) NULL, "
                + "action VARCHAR(64) NOT NULL, "
                + "detail TEXT NULL, "
                + "created_at BIGINT NOT NULL, "
                + "CONSTRAINT fk_farmer_logs_farmer FOREIGN KEY (farmer_id) REFERENCES farmers(id) ON DELETE CASCADE"
                + ")" + tableOptions
        );
    }
}
