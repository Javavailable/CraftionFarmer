package com.craftion.farmer.storage.migration;

import com.craftion.farmer.storage.DatabaseType;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class V2ProductStatesSchema implements Migration {

    @Override
    public int version() {
        return 2;
    }

    @Override
    public String name() {
        return "product_states_schema";
    }

    @Override
    public void migrate(Connection connection, DatabaseType databaseType) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(statement(databaseType));
        }
    }

    private String statement(DatabaseType databaseType) {
        if (databaseType == DatabaseType.MYSQL) {
            return "CREATE TABLE IF NOT EXISTS farmer_product_states ("
                + "farmer_id VARCHAR(36) NOT NULL, "
                + "material_key VARCHAR(128) NOT NULL, "
                + "collecting_enabled BOOLEAN NOT NULL DEFAULT TRUE, "
                + "PRIMARY KEY (farmer_id, material_key), "
                + "CONSTRAINT fk_farmer_product_states_farmer FOREIGN KEY (farmer_id) REFERENCES farmers(id) ON DELETE CASCADE"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        }
        return "CREATE TABLE IF NOT EXISTS farmer_product_states ("
            + "farmer_id TEXT NOT NULL, "
            + "material_key TEXT NOT NULL, "
            + "collecting_enabled INTEGER NOT NULL DEFAULT 1, "
            + "PRIMARY KEY (farmer_id, material_key), "
            + "FOREIGN KEY (farmer_id) REFERENCES farmers(id) ON DELETE CASCADE"
            + ")";
    }
}
